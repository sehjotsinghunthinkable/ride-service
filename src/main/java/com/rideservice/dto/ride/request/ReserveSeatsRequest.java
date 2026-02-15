package com.rideservice.dto.ride.request;

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
    private String userId;  // From Booking Service

    @NotBlank(message = "From stop is required")
    private String fromStop;

    @NotBlank(message = "To stop is required")
    private String toStop;

    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "At least 1 seat must be booked")
    private Integer seats;

    @Builder.Default
    private Integer expiryMinutes = 10; // Default 10 minutes
}