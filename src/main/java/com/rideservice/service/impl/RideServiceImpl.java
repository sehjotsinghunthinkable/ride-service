package com.rideservice.service.impl;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.dto.ride.response.RideDetailsResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.mapper.RideMapper;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.model.RideSegmentSeat;
import com.rideservice.model.RideStop;
import com.rideservice.repository.LocationRepository;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.LocationService;
import com.rideservice.service.RideService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideServiceImpl implements RideService {
    private final RideRepository rideRepository;
    private final RideMapper rideMapper;
    private final LocationRepository locationRepository;
    private final LocationService locationService;

    @Override
    @Transactional
    public RideResponseDto saveRideDetails(RideCreationDto rideCreationDto) {
        Ride ride = rideMapper.toRideWithSegmentMapping(rideCreationDto);
        rideRepository.save(ride);
        return rideMapper.toRideResponseDto(ride);
    }

    @Override
    public List<RideDetailResponse> getAllRideDetails(Locations from, Locations to, Date date) {
        // 1. Validate inputs
        validateSearchInputs(from, to, date);

        // 2. Get location entities
        Location fromLocation = locationService.getLocation(from.toString());
        Location toLocation = locationService.getLocation(to.toString());

        // 3. Convert date to LocalDateTime range
        LocalDate searchDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDateTime startOfDay = searchDate.atStartOfDay();
        LocalDateTime endOfDay = searchDate.plusDays(1).atStartOfDay();

        // 4. Fetch rides from repository with overlap logic
        List<RideDetailResponse> rides = rideRepository.getAllRideDetails(
                fromLocation.getId(),
                from.toString(),
                toLocation.getId(),
                to.toString(),
                startOfDay,
                endOfDay,
                100);

        // 5. Filter results
        return filterRides(rides);
    }

    private void validateSearchInputs(Locations from, Locations to, Date departure) {
        if (from == null || to == null || departure == null) {
            throw new IllegalArgumentException("All parameters are required");
        }

        // Check if departure date is in past
        LocalDate departureDate = departure.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        if (departureDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Departure date cannot be in the past");
        }

        // Check if from and to are same
        if (from.equals(to)) {
            throw new IllegalArgumentException("Source and destination cannot be same");
        }

        log.debug("Searching rides from {} to {} on {}", from, to, departureDate);
    }

    private List<RideDetailResponse> filterRides(
            List<RideDetailResponse> rides) {

        List<RideDetailResponse> filteredRides = new ArrayList<>();

        for (RideDetailResponse ride : rides) {
            try {
                // Skip rides with no available seats (query filters this too)
                if (ride.getAvailableSeats() == null || ride.getAvailableSeats() <= 0) {
                    log.debug("Skipping ride {} - no seats available", ride.getRideUuid());
                    continue;
                }

                // Skip rides where pickup time has passed (with buffer)
                if (ride.getStartTime() != null &&
                        ride.getStartTime().isBefore(LocalDateTime.now().minusMinutes(15))) {
                    log.debug("Skipping ride {} - pickup time passed", ride.getRideUuid());
                    continue;
                }
                filteredRides.add(ride);

            } catch (Exception e) {
                log.error("Error processing ride {}: {}", ride.getRideUuid(), e.getMessage());
            }
        }
        log.info("Found {} available rides", filteredRides.size());
        return filteredRides;
    }

    @Override
    public RideDetailsResponse getRideDetails(String rideUuid, Locations source, Locations destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Source and destination must be provided");
        }

        // Get ride with stops
        Ride ride = rideRepository.findByUuidWithStops(rideUuid)
                .orElseThrow(() -> new RuntimeException("Ride not found with uuid " + rideUuid));

        // Get segment seats separately
        List<RideSegmentSeat> segmentSeats = rideRepository
                .findSegmentSeatsByRideUuid(rideUuid);
        ride.setSegmentSeats(segmentSeats);

        List<RideStop> stops = ride.getRideStops().stream()
                .sorted(Comparator.comparingLong(RideStop::getSequence))
                .collect(Collectors.toList());

        if (stops.size() < 2) {
            throw new IllegalArgumentException("Ride stops not found");
        }

        Location sourceLocation = locationService.getLocation(source.toString());
        Location destinationLocation = locationService.getLocation(destination.toString());

        RideStop sourceStop = findStopByLocation(stops, sourceLocation);
        RideStop destStop = findStopByLocation(stops, destinationLocation);

        validateSegment(stops, sourceStop, destStop);

        // Calculate timing using cumulative offsets
        LocalDateTime pickupTime = ride.getStartTime()
                .plusMinutes(sourceStop.getDurationOffset());
        LocalDateTime dropTime = ride.getStartTime()
                .plusMinutes(destStop.getDurationOffset());

        Long segmentPrice = destStop.getPrice() - sourceStop.getPrice();

        Long availableSeats = calculateAvailableSeats(
                ride, sourceStop.getSequence(), destStop.getSequence());

        return buildResponse(ride, source, destination, pickupTime, dropTime, segmentPrice, availableSeats);
    }

    private RideStop findStopByLocation(List<RideStop> stops, Location location) {
        return stops.stream()
                .filter(stop -> stop.getStop().getId().equals(location.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Location " + location.getName() + " not found in ride stops"));
    }

    private void validateSegment(List<RideStop> stops, RideStop sourceStop, RideStop destStop) {
        if (sourceStop.getSequence() >= destStop.getSequence()) {
            throw new IllegalArgumentException(
                    "Source stop must come before destination stop. " +
                            "Source: " + sourceStop.getStop().getName() + " (seq: " + sourceStop.getSequence() + "), " +
                            "Dest: " + destStop.getStop().getName() + " (seq: " + destStop.getSequence() + ")");
        }

        // Validate cumulative values are increasing
        if (sourceStop.getDurationOffset() > destStop.getDurationOffset()) {
            throw new IllegalStateException(
                    "Duration offsets are inconsistent. Source: " + sourceStop.getDurationOffset() +
                            ", Dest: " + destStop.getDurationOffset());
        }

        if (sourceStop.getPrice() > destStop.getPrice()) {
            throw new IllegalStateException(
                    "Price values are inconsistent. Source: " + sourceStop.getPrice() +
                            ", Dest: " + destStop.getPrice());
        }
    }

    private RideDetailsResponse buildResponse(Ride ride, Locations source, Locations destination, LocalDateTime pickupTime, LocalDateTime dropTime, Long segmentPrice, Long availableSeats) {
        RideDetailsResponse response = new RideDetailsResponse();
        response.setRideUuid(ride.getUuid());
        response.setCarId(ride.getCarId());
        response.setDriverId(ride.getDriverId());
        response.setStartTime(pickupTime);
        response.setEndTime(dropTime);
        response.setPrice(segmentPrice);
        response.setAvailableSeats(availableSeats);
        response.setSource(source);
        response.setDestination(destination);
        return response;
    }

    private Long calculateAvailableSeats(Ride ride, Long fromSeq, Long toSeq) {
        return ride.getSegmentSeats().stream()
                .filter(segment -> overlaps(segment, fromSeq, toSeq))
                .mapToLong(RideSegmentSeat::getAvailableSeats)
                .min()
                .orElse(0L);
    }

    private boolean overlaps(RideSegmentSeat segment, Long fromSeq, Long toSeq) {
        // segment starts before drop AND ends after pickup
        return segment.getFromSequence() < toSeq
                && segment.getToSequence() > fromSeq;
    }

    @Transactional
    @Override
    public void cancelRide(String rideUuid) {
        Ride ride = rideRepository.findByUuid(rideUuid)
                .orElseThrow(() -> new RuntimeException("Ride with id : " + rideUuid + " doesn't exist "));

        // Check if ride can be cancelled
        if (hasConfirmedBookings(rideUuid)) {
            throw new IllegalStateException("Cannot cancel ride with confirmed bookings");
        }

        if (ride.getStartTime().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Cannot cancel ride that has already started");
        }

        // Soft delete
        ride.setDeleted(true);
        ride.setModifiedAt(LocalDateTime.now());
        rideRepository.save(ride);

        // soft delete associated stops and segments
        ride.getRideStops().forEach(stop -> {
            stop.setDeleted(true);
            stop.setModifiedAt(LocalDateTime.now());
        });

        ride.getSegmentSeats().forEach(segment -> {
            segment.setDeleted(true);
            segment.setModifiedAt(LocalDateTime.now());
        });
    }

    private boolean hasConfirmedBookings(String rideUuid) {
        // TODO
        return false;
    }

}
