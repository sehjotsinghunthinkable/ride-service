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
public class ReservationResponse {
    private String reservationId;
    private String rideUuid;
    private String fromStop;
    private String toStop;
    private Integer seatsReserved;
    private LocalDateTime expiresAt;
    private String status;  // RESERVED
    private Long price;
    private LocalDateTime pickupTime;
    private LocalDateTime dropTime;
}
