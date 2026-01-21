package com.rideservice.dto.ride.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StopsDto {
    private String name;
    private Long price;
    private Long durationOffset;
}
