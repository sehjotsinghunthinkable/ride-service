package com.rideservice.dto.ride.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseResponse {
    private String reservationId;
    private String status;  // CANCELLED or EXPIRED
    private String message;
}
