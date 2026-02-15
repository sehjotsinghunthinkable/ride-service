package com.rideservice.dto.ride.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RideResponseDto {
    private String rideUuid;
    private Long driverId;
    private Long carId;
    private LocalDateTime startTime;
    private Integer totalSeats;
    private Integer stopCount;  // number of stops
}
