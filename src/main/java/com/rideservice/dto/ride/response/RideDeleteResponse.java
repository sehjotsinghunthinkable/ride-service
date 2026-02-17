package com.rideservice.dto.ride.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideDeleteResponse {
    private String rideUuid;
    private String message;
    private boolean deleted;
    private int activeBookingCount;
}
