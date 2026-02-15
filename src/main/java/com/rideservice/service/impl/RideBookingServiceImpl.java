package com.rideservice.service.impl;

import com.rideservice.dto.ride.request.ReserveSeatsRequest;
import com.rideservice.dto.ride.request.RideSearchRequest;
import com.rideservice.dto.ride.response.ConfirmationResponse;
import com.rideservice.dto.ride.response.ReleaseResponse;
import com.rideservice.dto.ride.response.ReservationResponse;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.dto.ride.response.RideSearchResponse;
import com.rideservice.mapper.RideSearchMapper;
import com.rideservice.model.BookingStatus;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.model.RideBooking;
import com.rideservice.model.RideStop;
import com.rideservice.repository.LocationRepository;
import com.rideservice.repository.RideBookingRepository;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.RideBookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RideBookingServiceImpl implements RideBookingService {

    private final RideRepository rideRepository;
    private final LocationRepository locationRepository;
    private final RideSearchMapper rideSearchMapper;
    private final RideBookingRepository rideBookingRepository;

    public RideBookingServiceImpl(RideRepository rideRepository, LocationRepository locationRepository, RideSearchMapper rideSearchMapper, RideBookingRepository rideBookingRepository) {
        this.rideRepository = rideRepository;
        this.locationRepository = locationRepository;
        this.rideSearchMapper = rideSearchMapper;
        this.rideBookingRepository = rideBookingRepository;
    }
    @Transactional(readOnly = true)
    public List<RideSearchResponse> searchRides(RideSearchRequest request) {

        // 1. Validate request
        validateSearchRequest(request);

        // 2. Get location entities
        Location fromLocation = locationRepository.findByName(request.getFromStop())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Source location '%s' not found", request.getFromStop())));

        Location toLocation = locationRepository.findByName(request.getToStop())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Destination location '%s' not found", request.getToStop())));

        // 3. Prepare date range for the entire day
        LocalDateTime dayStart = request.getDepartureDate().toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        log.info("Searching rides: from='{}'({}), to='{}'({}), date={}, range=[{} to {}]",
                request.getFromStop(), fromLocation.getId(),
                request.getToStop(), toLocation.getId(),
                request.getDepartureDate(), dayStart, dayEnd);

        // 4. Execute search with projection
        List<RideSearchProjection> projections = rideRepository.searchAvailableRides(
                fromLocation.getId(),
                request.getFromStop(),
                toLocation.getId(),
                request.getToStop(),
                dayStart,
                dayEnd,
                100  // limit
        );

        // 5. Convert projections to response DTOs
        List<RideSearchResponse> responses = rideSearchMapper.toResponseList(projections);

        log.info("Search completed: found {} rides", responses.size());

        return responses;
    }

    private void validateSearchRequest(RideSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }

        if (request.getFromStop() == null || request.getFromStop().trim().isEmpty()) {
            throw new IllegalArgumentException("Source location is required");
        }

        if (request.getToStop() == null || request.getToStop().trim().isEmpty()) {
            throw new IllegalArgumentException("Destination location is required");
        }

        if (request.getFromStop().equals(request.getToStop())) {
            throw new IllegalArgumentException("Source and destination cannot be the same");
        }

        if (request.getDepartureDate() == null) {
            throw new IllegalArgumentException("Departure date is required");
        }

        if (request.getDepartureDate().toLocalDate().isBefore(LocalDateTime.now().toLocalDate())) {
            throw new IllegalArgumentException("Departure date cannot be in the past");
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReservationResponse reserveSeats(String rideUuid, ReserveSeatsRequest request) {
        log.info("Reserving {} seats for ride {} from {} to {} for user {}",
                request.getSeats(), rideUuid, request.getFromStop(),
                request.getToStop(), request.getUserId());

        // 1. Get ride with validation
        Ride ride = rideRepository.findByUuidWithStops(rideUuid)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found: " + rideUuid));

        // 2. Get stop sequences
        Location fromLocation = locationRepository.findByName(request.getFromStop())
                .orElseThrow(() -> new IllegalArgumentException("Source stop not found: " + request.getFromStop()));

        Location toLocation = locationRepository.findByName(request.getToStop())
                .orElseThrow(() -> new IllegalArgumentException("Destination stop not found: " + request.getToStop()));

        RideStop fromStop = ride.getRideStops().stream()
                .filter(rs -> rs.getStop().getId().equals(fromLocation.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Source stop not in ride"));

        RideStop toStop = ride.getRideStops().stream()
                .filter(rs -> rs.getStop().getId().equals(toLocation.getId()))
                .filter(rs -> rs.getSequence() > fromStop.getSequence())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid destination - must be after source"));

        // 3. Check availability with PESSIMISTIC LOCK
        Integer bookedSeats = rideBookingRepository.getTotalBookedSeatsWithLock(
                ride.getId(), fromStop.getSequence(), toStop.getSequence());

        int availableSeats = ride.getTotalSeats() - bookedSeats;
        log.debug("Ride {} has {} seats booked, {} available for segment {}->{}",
                rideUuid, bookedSeats, availableSeats, request.getFromStop(), request.getToStop());

        if (availableSeats < request.getSeats()) {
            throw new IllegalStateException(
                    String.format("Only %d seats available, but %d requested",
                            availableSeats, request.getSeats()));
        }

        // 4. Create reservation
        RideBooking booking = new RideBooking();
        booking.setBookingUuid(UUID.randomUUID().toString());
        booking.setRide(ride);
        booking.setUserId(request.getUserId());
        booking.setFromSequence(fromStop.getSequence());
        booking.setToSequence(toStop.getSequence());
        booking.setSeatsBooked(request.getSeats());
        booking.setStatus(BookingStatus.RESERVED);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(request.getExpiryMinutes()));

        RideBooking savedBooking = rideBookingRepository.save(booking);
        log.info("Reservation created with ID: {}, expires at: {}",
                savedBooking.getBookingUuid(), savedBooking.getExpiresAt());

        // 5. Build response
        return ReservationResponse.builder()
                .reservationId(savedBooking.getBookingUuid())
                .rideUuid(ride.getUuid())
                .fromStop(request.getFromStop())
                .toStop(request.getToStop())
                .seatsReserved(savedBooking.getSeatsBooked())
                .expiresAt(savedBooking.getExpiresAt())
                .status(savedBooking.getStatus().name())
                .price(toStop.getPrice() - fromStop.getPrice())
                .pickupTime(ride.getStartTime().plusMinutes(fromStop.getDurationOffset()))
                .dropTime(ride.getStartTime().plusMinutes(toStop.getDurationOffset()))
                .build();
    }

    @Override
    @Transactional
    public ConfirmationResponse confirmReservation(String reservationId) {
        log.info("Confirming reservation: {}", reservationId);

        // 1. Find reservation
        RideBooking booking = rideBookingRepository.findByBookingUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // 2. Validate can be confirmed
        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException(
                    String.format("Cannot confirm reservation with status: %s", booking.getStatus()));
        }

        if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            booking.setStatus(BookingStatus.EXPIRED);
            rideBookingRepository.save(booking);
            throw new IllegalStateException("Reservation has expired");
        }

        // 3. Update with optimistic locking
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        RideBooking confirmed = rideBookingRepository.save(booking);
        log.info("Reservation {} confirmed successfully", reservationId);

        return ConfirmationResponse.builder()
                .reservationId(confirmed.getBookingUuid())
                .rideUuid(confirmed.getRide().getUuid())
                .status(confirmed.getStatus().name())
                .confirmedAt(confirmed.getConfirmedAt())
                .finalPrice(calculatePrice(confirmed)) // Implement this
                .build();
    }

    @Override
    @Transactional
    public ReleaseResponse releaseReservation(String reservationId, String reason) {
        log.info("Releasing reservation: {}, reason: {}", reservationId, reason);

        RideBooking booking = rideBookingRepository.findByBookingUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException(
                    String.format("Cannot release reservation with status: %s", booking.getStatus()));
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason);

        rideBookingRepository.save(booking);
        log.info("Reservation {} released/cancelled", reservationId);

        return ReleaseResponse.builder()
                .reservationId(booking.getBookingUuid())
                .status(booking.getStatus().name())
                .message("Reservation cancelled successfully")
                .build();
    }

    @Override
    @Transactional
    public ReleaseResponse expireReservation(String reservationId) {
        log.info("Expiring reservation: {}", reservationId);

        RideBooking booking = rideBookingRepository.findByBookingUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        booking.setStatus(BookingStatus.EXPIRED);
        booking.setCancellationReason("Auto-expired after " + booking.getExpiresAt());

        rideBookingRepository.save(booking);

        return ReleaseResponse.builder()
                .reservationId(booking.getBookingUuid())
                .status(booking.getStatus().name())
                .message("Reservation expired")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationStatus(String reservationId) {
        RideBooking booking = rideBookingRepository.findByBookingUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        Ride ride = booking.getRide();

        // Get stop names
        String fromStopName = getStopNameBySequence(ride, booking.getFromSequence());
        String toStopName = getStopNameBySequence(ride, booking.getToSequence());

        return ReservationResponse.builder()
                .reservationId(booking.getBookingUuid())
                .rideUuid(ride.getUuid())
                .fromStop(fromStopName)
                .toStop(toStopName)
                .seatsReserved(booking.getSeatsBooked())
                .expiresAt(booking.getExpiresAt())
                .status(booking.getStatus().name())
                .build();
    }

    private Long calculatePrice(RideBooking booking) {
        Ride ride = booking.getRide();
        RideStop fromStop = ride.getRideStops().stream()
                .filter(rs -> rs.getSequence().equals(booking.getFromSequence()))
                .findFirst().orElseThrow();
        RideStop toStop = ride.getRideStops().stream()
                .filter(rs -> rs.getSequence().equals(booking.getToSequence()))
                .findFirst().orElseThrow();

        return toStop.getPrice() - fromStop.getPrice();
    }

    private String getStopNameBySequence(Ride ride, Long sequence) {
        return ride.getRideStops().stream()
                .filter(rs -> rs.getSequence().equals(sequence))
                .findFirst()
                .map(rs -> rs.getStop().getName())
                .orElse("Unknown");
    }
}
