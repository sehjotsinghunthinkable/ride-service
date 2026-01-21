package com.rideservice.dto.ride.response;

import java.time.LocalDateTime;

public interface ListingResponseDto {
    Long getRideId();
    Long getAvailableSeats();
    String getFrom();
    String getTo();
    Long getPrice();
    LocalDateTime getTime();
    Long getDriverId();
    Long getCarId();
}
