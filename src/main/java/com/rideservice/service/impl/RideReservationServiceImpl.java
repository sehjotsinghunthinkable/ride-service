package com.rideservice.service.impl;

import com.rideservice.dto.ride.request.ReserveSeatsRequest;
import com.rideservice.dto.ride.request.RideSearchRequest;
import com.rideservice.dto.ride.response.ConfirmationResponse;
import com.rideservice.dto.ride.response.ReleaseResponse;
import com.rideservice.dto.ride.response.ReservationResponse;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.dto.ride.response.RideSearchResponse;
import com.rideservice.exception.SeatUnavailableException;
import com.rideservice.mapper.RideSearchMapper;
import com.rideservice.constants.enums.BookingStatus;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.model.RideReservation;
import com.rideservice.model.RideStop;
import com.rideservice.repository.LocationRepository;
import com.rideservice.repository.RideReservationRepository;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.RideReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.rideservice.constants.enums.ApplicationConstants.RIDE_DETAILS_CACHE;
import static com.rideservice.constants.enums.ApplicationConstants.SEARCH_RESULTS_CACHE;
import static com.rideservice.utils.AuditDetailUtil.addRideBookingCreationDetails;

@Service
@Slf4j
public class RideReservationServiceImpl implements RideReservationService {

    private final RideRepository rideRepository;
    private final LocationRepository locationRepository;
    private final RideSearchMapper rideSearchMapper;
    private final RideReservationRepository rideReservationRepository;

    public RideReservationServiceImpl(RideRepository rideRepository, LocationRepository locationRepository, RideSearchMapper rideSearchMapper, RideReservationRepository rideReservationRepository) {
        this.rideRepository = rideRepository;
        this.locationRepository = locationRepository;
        this.rideSearchMapper = rideSearchMapper;
        this.rideReservationRepository = rideReservationRepository;
    }

    @Cacheable(value = SEARCH_RESULTS_CACHE,
            key = "#request.fromStop + ':' + #request.toStop + ':' + #request.departureDate",
            unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<RideSearchResponse> searchRides(RideSearchRequest request) {

        // 1. Validate request
        validateSearchRequest(request);

        // 2. Get location entities
        Location fromLocation = locationRepository.findByName(request.getFromStop().toString().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Source location '%s' not found", request.getFromStop())));

        Location toLocation = locationRepository.findByName(request.getToStop().toString().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Destination location '%s' not found", request.getToStop())));

        // 3. Prepare date range for the entire day
        LocalDateTime dayStart = request.getDepartureDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        log.info("Searching rides: from='{}'({}), to='{}'({}), date={}, range=[{} to {}]",
                request.getFromStop(), fromLocation.getId(),
                request.getToStop(), toLocation.getId(),
                request.getDepartureDate(), dayStart, dayEnd);

        // 4. Execute search with projection
        List<RideSearchProjection> projections = rideRepository.searchAvailableRides(
                fromLocation.getId(),
                request.getFromStop().toString(),
                toLocation.getId(),
                request.getToStop().toString(),
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

        if (request.getFromStop() == null) {
            throw new IllegalArgumentException("Source location is required");
        }

        if (request.getToStop() == null) {
            throw new IllegalArgumentException("Destination location is required");
        }

        if (request.getFromStop().equals(request.getToStop())) {
            throw new IllegalArgumentException("Source and destination cannot be the same");
        }

        if (request.getDepartureDate() == null) {
            throw new IllegalArgumentException("Departure date is required");
        }

        if (request.getDepartureDate().isBefore(LocalDateTime.now().toLocalDate())) {
            throw new IllegalArgumentException("Departure date cannot be in the past");
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CacheEvict(
            value = {
                    SEARCH_RESULTS_CACHE,
                    RIDE_DETAILS_CACHE
            },
            allEntries = true
    )
    public ReservationResponse reserveSeats(String rideUuid, ReserveSeatsRequest request) {
        log.info("Reserving {} seats for ride {} from {} to {} for user {}",
                request.getSeats(), rideUuid, request.getFromStop(),
                request.getToStop(), request.getUserId());

        // 1. Get ride with validation
        Ride ride = rideRepository.findByUuidWithStops(rideUuid)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found: " + rideUuid));

        // 2. Get stop sequences
        Location fromLocation = locationRepository.findByName(request.getFromStop().toString().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Source stop not found: " + request.getFromStop()));

        Location toLocation = locationRepository.findByName(request.getToStop().toString().toLowerCase())
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
        Integer bookedSeats = rideReservationRepository.getTotalBookedSeatsWithLock(
                ride.getId(), fromStop.getSequence(), toStop.getSequence());

        int availableSeats = ride.getTotalSeats() - bookedSeats;
        log.debug("Ride {} has {} seats booked, {} available for segment {}->{}",
                rideUuid, bookedSeats, availableSeats, request.getFromStop(), request.getToStop());

        if (availableSeats < request.getSeats()) {
            throw new SeatUnavailableException(
                    String.format("Only %d seats available, but %d requested",
                            availableSeats, request.getSeats())
            );
        }

        // 4. Create reservation
        RideReservation booking = new RideReservation();
        booking.setReservationUuid(UUID.randomUUID().toString());
        booking.setRide(ride);
        booking.setUserId(request.getUserId());
        booking.setFromSequence(fromStop.getSequence());
        booking.setToSequence(toStop.getSequence());
        booking.setSeatsBooked(request.getSeats());
        booking.setStatus(BookingStatus.RESERVED);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(request.getExpiryMinutes()));
        addRideBookingCreationDetails(booking);
        RideReservation savedBooking = rideReservationRepository.save(booking);
        log.info("Reservation created with ID: {}, expires at: {}",
                savedBooking.getReservationUuid(), savedBooking.getExpiresAt());

        // 5. Build response
        return ReservationResponse.builder()
                .reservationId(savedBooking.getReservationUuid())
                .rideUuid(ride.getUuid())
                .fromStop(request.getFromStop().toString())
                .toStop(request.getToStop().toString())
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
    @CacheEvict(
            value = {
                    SEARCH_RESULTS_CACHE,
                    RIDE_DETAILS_CACHE
            },
            allEntries = true
    )
    public ConfirmationResponse confirmReservation(String reservationId) {
        log.info("Confirming reservation: {}", reservationId);

        // 1. Find reservation
        RideReservation booking = rideReservationRepository.findByReservationUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // 2. Validate can be confirmed
        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException(
                    String.format("Cannot confirm reservation with status: %s", booking.getStatus()));
        }

        if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            booking.setStatus(BookingStatus.EXPIRED);
            rideReservationRepository.save(booking);
            throw new IllegalStateException("Reservation has expired");
        }

        // 3. Update with optimistic locking
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        RideReservation confirmed = rideReservationRepository.save(booking);
        log.info("Reservation {} confirmed successfully", reservationId);

        return ConfirmationResponse.builder()
                .reservationId(confirmed.getReservationUuid())
                .rideUuid(confirmed.getRide().getUuid())
                .status(confirmed.getStatus().name())
                .confirmedAt(confirmed.getConfirmedAt())
                .finalPrice(calculatePrice(confirmed)) // Implement this
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(
            value = {
                    SEARCH_RESULTS_CACHE,
                    RIDE_DETAILS_CACHE
            },
            allEntries = true
    )
    public ReleaseResponse releaseReservation(String reservationId, String reason) {
        log.info("Releasing reservation: {}, reason: {}", reservationId, reason);

        RideReservation booking = rideReservationRepository.findByReservationUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException(
                    String.format("Cannot release reservation with status: %s", booking.getStatus()));
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason);

        rideReservationRepository.save(booking);
        log.info("Reservation {} released/cancelled", reservationId);

        return ReleaseResponse.builder()
                .reservationId(booking.getReservationUuid())
                .status(booking.getStatus().name())
                .message("Reservation cancelled successfully")
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(
            value = {
                    SEARCH_RESULTS_CACHE,
                    RIDE_DETAILS_CACHE
            },
            allEntries = true
    )
    public ReleaseResponse expireReservation(String reservationId) {
        log.info("Expiring reservation: {}", reservationId);

        RideReservation booking = rideReservationRepository.findByReservationUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        booking.setStatus(BookingStatus.EXPIRED);
        booking.setCancellationReason("Auto-expired after " + booking.getExpiresAt());

        rideReservationRepository.save(booking);

        return ReleaseResponse.builder()
                .reservationId(booking.getReservationUuid())
                .status(booking.getStatus().name())
                .message("Reservation expired")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationStatus(String reservationId) {
        RideReservation booking = rideReservationRepository.findByReservationUuid(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        Ride ride = booking.getRide();

        // Get stop names
        RideStop fromStop = getRideStopBySequence(ride, booking.getFromSequence());
        RideStop toStop = getRideStopBySequence(ride, booking.getToSequence());
        String fromStopName = fromStop.getStop().getName();
        String toStopName = toStop.getStop().getName();
        LocalDateTime pickupTime = ride.getStartTime().plusMinutes(fromStop.getDurationOffset());
        LocalDateTime dropTime = ride.getStartTime().plusMinutes(toStop.getDurationOffset());
        Long price = toStop.getPrice() - fromStop.getPrice();

        return ReservationResponse.builder()
                .reservationId(booking.getReservationUuid())
                .rideUuid(ride.getUuid())
                .fromStop(fromStopName)
                .toStop(toStopName)
                .seatsReserved(booking.getSeatsBooked())
                .expiresAt(booking.getExpiresAt())
                .status(booking.getStatus().name())
                .dropTime(dropTime)
                .pickupTime(pickupTime)
                .price(price)
                .build();
    }

    private Long calculatePrice(RideReservation booking) {
        Ride ride = booking.getRide();
        RideStop fromStop = ride.getRideStops().stream()
                .filter(rs -> rs.getSequence().equals(booking.getFromSequence()))
                .findFirst().orElseThrow();
        RideStop toStop = ride.getRideStops().stream()
                .filter(rs -> rs.getSequence().equals(booking.getToSequence()))
                .findFirst().orElseThrow();

        return toStop.getPrice() - fromStop.getPrice();
    }

    private RideStop getRideStopBySequence(Ride ride, Long sequence) {
        return ride.getRideStops().stream()
                .filter(rs -> rs.getSequence().equals(sequence))
                .findFirst()
                .orElse(null);
    }
}
