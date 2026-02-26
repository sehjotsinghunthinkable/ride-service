package com.rideservice.service.impl;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.request.RideUpdateRequest;
import com.rideservice.dto.ride.request.StopsDto;
import com.rideservice.dto.ride.response.RideDeleteResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.dto.ride.response.RideUpdateResponse;
import com.rideservice.mapper.RideMapper;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.model.RideStop;
import com.rideservice.repository.LocationRepository;
import com.rideservice.repository.RideRepository;
import com.rideservice.repository.RideReservationRepository;
import com.rideservice.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.rideservice.constants.enums.ApplicationConstants.RIDE_DETAILS_CACHE;
import static com.rideservice.constants.enums.ApplicationConstants.SEARCH_RESULTS_CACHE;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideServiceImpl implements RideService {

    private final RideRepository rideRepository;
    private final RideMapper rideMapper;
    private final LocationRepository locationRepository;
    private final RideReservationRepository rideReservationRepository;

    @CacheEvict(
            value = {
                    SEARCH_RESULTS_CACHE,
                    RIDE_DETAILS_CACHE
            },
            allEntries = true
    )
    @Transactional
    public RideResponseDto saveRideDetails(RideCreationDto rideCreationDto) {
        log.info("Creating new ride with {} stops",
                rideCreationDto.getStops() != null ? rideCreationDto.getStops().size() : 0);

        Ride ride = rideMapper.toRide(rideCreationDto);

        Ride savedRide = rideRepository.save(ride);

        log.info("Successfully created ride with UUID: {}, total seats: {}",
                savedRide.getUuid(), savedRide.getTotalSeats());

        return rideMapper.toRideResponseDto(savedRide);
    }

    @Override
    @Cacheable(value = RIDE_DETAILS_CACHE,
            key = "#rideUuid + ':' + #from + ':' + #to",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public RideSearchProjection fetchRideDetails(String rideUuid, Locations from, Locations to) {
        validateRideDetailsRequest(rideUuid, from, to);
        return rideRepository.getRideDetails(rideUuid, from.toString().toLowerCase(), to.toString().toLowerCase());
    }

    private void validateRideDetailsRequest(String rideUuid,
                                            Locations from,
                                            Locations to) {

        if (rideUuid == null || rideUuid.isBlank()) {
            throw new IllegalArgumentException("Ride UUID is required");
        }

        if (from == null) {
            throw new IllegalArgumentException("Source location is required");
        }

        if (to == null) {
            throw new IllegalArgumentException("Destination location is required");
        }

        if (from.equals(to)) {
            throw new IllegalArgumentException("Source and destination cannot be the same");
        }

        Ride ride = rideRepository.findByUuidWithStops(rideUuid)
                .orElseThrow(() ->
                        new IllegalArgumentException("Ride not found with uuid: " + rideUuid));

        Long fromSequence = null;
        Long toSequence = null;

        for (RideStop stop : ride.getRideStops()) {

            if (stop.getStop().getName()
                    .equalsIgnoreCase(from.toString())) {
                fromSequence = stop.getSequence();
            }

            if (stop.getStop().getName()
                    .equalsIgnoreCase(to.toString())) {
                toSequence = stop.getSequence();
            }
        }

        if (fromSequence == null) {
            throw new IllegalArgumentException(
                    "Source stop not part of this ride: " + from);
        }

        if (toSequence == null) {
            throw new IllegalArgumentException(
                    "Destination stop not part of this ride: " + to);
        }

        if (toSequence <= fromSequence) {
            throw new IllegalArgumentException(
                    "Destination must come after source in ride route");
        }
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
    public RideUpdateResponse updateRide(String rideUuid, RideUpdateRequest request) {
        log.info("Attempting to update ride: {}", rideUuid);

        Ride ride = rideRepository.findByUuidWithStops(rideUuid)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found: " + rideUuid));

        boolean hasActiveBookings = rideReservationRepository.hasActiveBookings(rideUuid);
        int activeBookingCount = rideReservationRepository.getActiveBookingCount(rideUuid);

        if (hasActiveBookings) {
            log.warn("Cannot update ride {} - has {} active bookings", rideUuid, activeBookingCount);
            return RideUpdateResponse.builder()
                    .rideUuid(rideUuid)
                    .message(String.format("Cannot update ride with %d active booking(s)", activeBookingCount))
                    .activeBookingCount(activeBookingCount)
                    .build();
        }

        validateUpdateRequest(request);

        if (request.getStartTime() != null) {
            ride.setStartTime(request.getStartTime());
        }

        if (request.getTotalSeats() != null) {
            ride.setTotalSeats(request.getTotalSeats());
        }

        if (request.getStops() != null && !request.getStops().isEmpty()) {
            if (request.getStops().size() < 2) {
                throw new IllegalArgumentException("At least 2 stops are required");
            }
            ride.getRideStops().clear();
            List<RideStop> newStops = createStopsFromRequest(request.getStops(), ride);
            newStops.forEach(ride::addRideStop);
        }
        ride.setModifiedAt(LocalDateTime.now());
        rideRepository.save(ride);
        log.info("Successfully updated ride: {}", rideUuid);

        return RideUpdateResponse.builder()
                .rideUuid(rideUuid)
                .message("Ride updated successfully")
                .activeBookingCount(0)
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
    public RideDeleteResponse deleteRide(String rideUuid) {
        log.info("Attempting to delete ride: {}", rideUuid);

        Ride ride = rideRepository.findByUuid(rideUuid)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found: " + rideUuid));

        boolean hasActiveBookings = rideReservationRepository.hasActiveBookings(rideUuid);
        int activeBookingCount = rideReservationRepository.getActiveBookingCount(rideUuid);

        if (hasActiveBookings) {
            log.warn("Cannot delete ride {} - has {} active bookings", rideUuid, activeBookingCount);
            return RideDeleteResponse.builder()
                    .rideUuid(rideUuid)
                    .message(String.format("Cannot delete ride with %d active booking(s)", activeBookingCount))
                    .deleted(false)
                    .activeBookingCount(activeBookingCount)
                    .build();
        }

        ride.setDeleted(true);
        log.info("Ride {} has no bookings - performing soft delete", rideUuid);
        rideRepository.save(ride);

        log.info("Successfully deleted ride: {}", rideUuid);
        return RideDeleteResponse.builder()
                .rideUuid(rideUuid)
                .message("Ride deleted successfully")
                .deleted(true)
                .activeBookingCount(0)
                .build();
    }

    private void validateUpdateRequest(RideUpdateRequest request) {
        if (request.getStops() != null) {
            long cumulativeDuration = 0;
            long cumulativePrice = 0;

            for (int i = 0; i < request.getStops().size(); i++) {
                StopsDto stop = request.getStops().get(i);

                if (i == 0) {
                    if (stop.getDurationOffset() != 0) {
                        throw new IllegalArgumentException("First stop must have durationOffset = 0");
                    }
                    if (stop.getPrice() != 0) {
                        throw new IllegalArgumentException("First stop must have price = 0");
                    }
                } else {
                    if (stop.getDurationOffset() <= 0) {
                        throw new IllegalArgumentException(
                                "Duration offset must be positive for stop: " + stop.getName());
                    }
                    if (stop.getPrice() <= 0) {
                        throw new IllegalArgumentException(
                                "Price must be positive for stop: " + stop.getName());
                    }

                    cumulativeDuration += stop.getDurationOffset();
                    cumulativePrice += stop.getPrice();
                }
            }
        }
    }

    private List<RideStop> createStopsFromRequest(List<StopsDto> stopDtos, Ride ride) {
        List<RideStop> stops = new ArrayList<>();
        long cumulativeDuration = 0;
        long cumulativePrice = 0;

        for (int i = 0; i < stopDtos.size(); i++) {
            StopsDto dto = stopDtos.get(i);

            Location location = locationRepository.findByName(dto.getName().name().toLowerCase())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Location not found: " + dto.getName()));

            RideStop stop = new RideStop();
            stop.setStop(location);
            stop.setSequence((long) i);
            stop.setRide(ride);
            stop.setUuid(UUID.randomUUID().toString());
            stop.setCreatedAt(LocalDateTime.now());

            if (i == 0) {
                stop.setDurationOffset(0L);
                stop.setPrice(0L);
            } else {
                cumulativeDuration += dto.getDurationOffset();
                cumulativePrice += dto.getPrice();

                stop.setDurationOffset(cumulativeDuration);
                stop.setPrice(cumulativePrice);
            }
            stops.add(stop);
        }
        return stops;
    }
}
