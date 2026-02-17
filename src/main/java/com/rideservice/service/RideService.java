package com.rideservice.service;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.request.RideUpdateRequest;
import com.rideservice.dto.ride.response.RideDeleteResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.dto.ride.response.RideUpdateResponse;

public interface RideService {
    RideResponseDto saveRideDetails(RideCreationDto rideCreationDto);

    RideSearchProjection fetchRideDetails(String rideUuid, Locations from, Locations to);

    RideUpdateResponse updateRide(String rideUuid, RideUpdateRequest request);

    RideDeleteResponse deleteRide(String rideUuid);
}
