package com.rideservice.dto.ride.response;

import java.time.LocalDateTime;

public interface RideSearchProjection {
    String getRideUuid();
    String getFromStop();
    String getToStop();
    LocalDateTime getPickupTime();
    LocalDateTime getDropTime();
    Long getPrice();
    Integer getAvailableSeats();
    Long getDriverId();
    Long getCarId();
}