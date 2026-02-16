package com.rideservice.service;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.dto.ride.response.RideSearchProjection;

public interface RideService {
    RideResponseDto saveRideDetails(RideCreationDto rideCreationDto);

    RideSearchProjection fetchRideDetails(String rideUuid, Locations from, Locations to);
}
