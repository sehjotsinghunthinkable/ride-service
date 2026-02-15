package com.rideservice.service.impl;

import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.mapper.RideMapper;
import com.rideservice.model.Ride;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideServiceImpl implements RideService {
    private final RideRepository rideRepository;
    private final RideMapper rideMapper;

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
}
