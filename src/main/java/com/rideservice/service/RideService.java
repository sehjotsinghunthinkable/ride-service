package com.rideservice.service;

import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.ListingResponseDto;
import com.rideservice.dto.ride.response.RideResponseDto;

import java.util.Date;
import java.util.List;

public interface RideService {
    RideResponseDto saveRideDetails(RideCreationDto rideCreationDto);

    List<ListingResponseDto> getAllRideDetails(String from, String to, Date date);
}
