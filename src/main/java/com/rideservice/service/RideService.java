package com.rideservice.service;

import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideResponseDto;

public interface RideService {
    RideResponseDto saveRideDetails(RideCreationDto rideCreationDto);
}
