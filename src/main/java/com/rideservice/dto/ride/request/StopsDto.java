package com.rideservice.dto.ride.request;

import com.rideservice.constants.enums.Locations;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StopsDto {
    private Locations name;
    private Long price;
    private Long durationOffset;
}
