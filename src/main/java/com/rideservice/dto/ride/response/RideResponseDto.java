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
    private Long id;
    private LocalDateTime startTime;
    private Long driverId;
    private Long carId;
    private List<StopsResponseDto> stops;
}
