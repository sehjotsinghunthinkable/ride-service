package com.rideservice.controller;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.ReserveSeatsRequest;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.request.RideSearchRequest;
import com.rideservice.dto.ride.request.RideUpdateRequest;
import com.rideservice.dto.ride.response.ConfirmationResponse;
import com.rideservice.dto.ride.response.ReleaseResponse;
import com.rideservice.dto.ride.response.ReservationResponse;
import com.rideservice.dto.ride.response.RideDeleteResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.dto.ride.response.RideSearchResponse;
import com.rideservice.dto.ride.response.RideUpdateResponse;
import com.rideservice.service.RideReservationService;
import com.rideservice.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/ride")
@RequiredArgsConstructor
@Slf4j
public class RideController {
    private final RideService rideService;
    private final RideReservationService rideReservationService;

    @PostMapping("/create")
    @Operation(summary = "Create new ride",
            description = "Create a new ride, defining seats, locations and timings ")
    public RideResponseDto saveRideDetails(@Valid @RequestBody RideCreationDto rideCreationDto){
        return rideService.saveRideDetails(rideCreationDto);
    }

    @GetMapping("/{rideUuid}")
    @Operation(summary = "Fetch ride details based on uuid, pickup and drop off locations ")
    public RideSearchProjection fetchRideDetails(
            @PathVariable String rideUuid,
            @RequestParam Locations from,
            @RequestParam Locations to
    ){
        return rideService.fetchRideDetails(rideUuid, from, to);
    }

    @GetMapping("/search")
    @Operation(summary = "Search for available rides",
            description = "Find rides between two stops on a specific date")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved rides"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<RideSearchResponse>> searchRides(
            @RequestParam Locations from,
            @RequestParam Locations to,

            @Parameter(description = "Departure date",
                    example = "2024-01-15", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate departureDate) {

        log.info("Received search request: from={}, to={}, date={}", from, to, departureDate);

        RideSearchRequest request = RideSearchRequest.builder()
                .fromStop(from)
                .toStop(to)
                .departureDate(departureDate)
                .build();

        List<RideSearchResponse> rides = rideReservationService.searchRides(request);
        log.info("Returning {} rides for search request", rides.size());
        return ResponseEntity.ok(rides);
    }

    @PostMapping("/rides/{rideUuid}/reserve")
    @Operation(summary = "Reserve seats on a ride")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Seats reserved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Not enough seats available")
    })
    public ResponseEntity<ReservationResponse> reserveSeats(
            @Parameter(description = "Ride UUID", required = true)
            @PathVariable String rideUuid,

            @Valid @RequestBody ReserveSeatsRequest request) {

        log.info("POST /rides/{}/reserve - {} seats from {} to {}",
                rideUuid, request.getSeats(), request.getFromStop(), request.getToStop());

        ReservationResponse response = rideReservationService.reserveSeats(rideUuid, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    @Operation(summary = "Confirm a reservation after successful payment")
    public ResponseEntity<ConfirmationResponse> confirmReservation(
            @Parameter(description = "Reservation UUID", required = true)
            @PathVariable String reservationId) {

        log.info("POST /reservations/{}/confirm", reservationId);
        ConfirmationResponse response = rideReservationService.confirmReservation(reservationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Release/cancel a reservation (payment failed or user cancelled)")
    public ResponseEntity<ReleaseResponse> releaseReservation(
            @Parameter(description = "Reservation UUID", required = true)
            @PathVariable String reservationId,

            @RequestParam(required = false, defaultValue = "User cancelled") String reason) {

        log.info("POST /reservations/{}/release - reason: {}", reservationId, reason);

        ReleaseResponse response = rideReservationService.releaseReservation(reservationId, reason);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(summary = "Get reservation status")
    public ResponseEntity<ReservationResponse> getReservationStatus(
            @Parameter(description = "Reservation UUID", required = true)
            @PathVariable String reservationId) {

        log.info("GET /reservations/{}", reservationId);
        ReservationResponse response = rideReservationService.getReservationStatus(reservationId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{rideUuid}")
    @Operation(summary = "Update ride details ")
    public ResponseEntity<RideUpdateResponse> updateRide(
            @Parameter(description = "Ride UUID", required = true)
            @PathVariable String rideUuid,
            @Valid @RequestBody RideUpdateRequest request) {

        RideUpdateResponse response = rideService.updateRide(rideUuid, request);
        if (response.getActiveBookingCount() > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{rideUuid}")
    @Operation(summary = "Delete ride ")
    public ResponseEntity<RideDeleteResponse> deleteRide(
            @Parameter(description = "Ride UUID", required = true)
            @PathVariable String rideUuid) {

        RideDeleteResponse response = rideService.deleteRide(rideUuid);
        if (!response.isDeleted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
