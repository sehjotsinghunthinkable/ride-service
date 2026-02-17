package com.rideservice.dto.ride.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "Start time cannot be null.")
    private LocalDateTime startTime;

    @NotNull(message = "Total seats cannot be null.")
    @Min(value = 1, message = "Total seats must be at least 1.")
    private Integer totalSeats;

    @NotNull(message = "Driver Id cannot be null.")
    private Long driverId;

    @NotNull(message = "Car Id cannot be null.")
    private Long carId;

    @Valid
    private List<StopsDto> stops;
}
