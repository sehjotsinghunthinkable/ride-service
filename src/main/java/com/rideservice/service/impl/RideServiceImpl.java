package com.rideservice.service.impl;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.request.RideSearchRequest;
import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.mapper.RideMapper;
import com.rideservice.model.Ride;
import com.rideservice.model.RideStop;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rideservice.constants.enums.ApplicationConstants.RIDE_DETAILS_CACHE;
import static com.rideservice.constants.enums.ApplicationConstants.SEARCH_RESULTS_CACHE;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideServiceImpl implements RideService {


    private final RideRepository rideRepository;
    private final RideMapper rideMapper;

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

        // 1. Convert DTO to entity
        Ride ride = rideMapper.toRide(rideCreationDto);

        // 2. Save ride
        Ride savedRide = rideRepository.save(ride);

        log.info("Successfully created ride with UUID: {}, total seats: {}",
                savedRide.getUuid(), savedRide.getTotalSeats());

        // 3. Convert to response DTO
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

}
