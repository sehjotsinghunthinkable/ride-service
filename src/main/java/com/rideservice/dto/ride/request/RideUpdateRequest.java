package com.rideservice.dto.ride.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideUpdateRequest {
    private LocalDateTime startTime;
    @Min(value = 1, message = "Total seats must be at least 1.")
    private Integer totalSeats;
    @Valid
    private List<StopsDto> stops;
}
