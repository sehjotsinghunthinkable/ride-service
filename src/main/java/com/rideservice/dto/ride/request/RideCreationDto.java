package com.rideservice.dto.ride.request;

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
public class RideCreationDto {
    private LocalDateTime startTime;
    private Integer totalSeats;
    private Long driverId;
    private Long carId;
    private List<StopsDto> stops;
}
