package com.rideservice.dto.ride.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StopsResponseDto {
    private Long id;
    private String name;
    private Long price;
    private Long durationOffset;
    private Long sequence;
}
