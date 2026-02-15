package com.rideservice.mapper;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.dto.ride.response.RideSearchResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RideSearchMapper {

    public RideSearchResponse toResponse(RideSearchProjection projection) {
        if (projection == null) {
            return null;
        }

        return RideSearchResponse.builder()
                .rideUuid(projection.getRideUuid())
                .fromStop(projection.getFromStop())
                .toStop(projection.getToStop())
                .pickupTime(projection.getPickupTime())
                .dropTime(projection.getDropTime())
                .price(projection.getPrice())
                .availableSeats(projection.getAvailableSeats())
                .driverId(projection.getDriverId())
                .carId(projection.getCarId())
                .build();
    }

    public List<RideSearchResponse> toResponseList(List<RideSearchProjection> projections) {
        if (projections == null) {
            return List.of();
        }

        return projections.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}