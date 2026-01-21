package com.rideservice.service.impl;

import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.ListingResponseDto;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.mapper.RideMapper;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.repository.LocationRepository;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.RideService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideServiceImpl implements RideService {
    private final RideRepository rideRepository;
    private final RideMapper rideMapper;
    private final LocationRepository locationRepository;

    @Override
    @Transactional
    public RideResponseDto saveRideDetails(RideCreationDto rideCreationDto) {
        Ride ride = rideMapper.toRideWithSegmentMapping(rideCreationDto);
        rideRepository.save(ride);
        return rideMapper.toRideResponseDto(ride);
    }

    @Override
    public List<ListingResponseDto> getAllRideDetails(String from, String to, Date date) {
        Location fromLocation = locationRepository.findByName(from).orElse(null);
        Location toLocation = locationRepository.findByName(to).orElse(null);
        if(fromLocation == null || toLocation == null){
            throw new IllegalArgumentException("From or To location is null");
        }
        LocalDate localDateTime =
                date.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
        LocalDateTime startOfDay = localDateTime.atStartOfDay();
        LocalDateTime endOfDay = localDateTime.plusDays(1).atStartOfDay();
        return rideRepository.getAllRideDetails(fromLocation.getId(), toLocation.getId(), startOfDay, endOfDay).stream().filter(e->e.getAvailableSeats()>0).toList();
    }
}
