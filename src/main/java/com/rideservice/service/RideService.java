package com.rideservice.service;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.dto.ride.response.RideDetailsResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;

public interface RideService {
    RideResponseDto saveRideDetails(RideCreationDto rideCreationDto);

    List<RideDetailResponse> getAllRideDetails(Locations from, Locations to, Date date);

    RideDetailsResponse getRideDetails(String rideUuid, Locations source, Locations destination);

    void cancelRide(String rideUuid);
}
