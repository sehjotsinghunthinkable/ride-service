package com.rideservice.dto.ride.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationResponse {
    private String reservationId;
    private String rideUuid;
    private String status;
    private LocalDateTime confirmedAt;
    private Long finalPrice;
}
