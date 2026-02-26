package com.rideservice.dto.ride.request;

import com.rideservice.constants.enums.Locations;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Price cannot be null.")
    private Long price;

    @NotNull(message = "Duration Offset cannot be null.")
    private Long durationOffset;
}
