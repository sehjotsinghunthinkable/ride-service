package com.rideservice.dto.ride.request;

import com.rideservice.constants.enums.Locations;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveSeatsRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    private Locations fromStop;

    private Locations toStop;

    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "At least 1 seat must be booked")
    private Integer seats;

    private String bookingUuid;
}