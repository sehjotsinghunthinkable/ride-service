package com.rideservice.dto.ride.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideUpdateResponse {
    private String rideUuid;
    private String message;
    private int activeBookingCount;  // number of bookings that prevented update
}
