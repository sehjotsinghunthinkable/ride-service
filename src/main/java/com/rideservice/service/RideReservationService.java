package com.rideservice.service;

import com.rideservice.dto.ride.request.ReserveSeatsRequest;
import com.rideservice.dto.ride.request.RideSearchRequest;
import com.rideservice.dto.ride.response.ConfirmationResponse;
import com.rideservice.dto.ride.response.ReleaseResponse;
import com.rideservice.dto.ride.response.ReservationResponse;
import com.rideservice.dto.ride.response.RideSearchResponse;

import java.util.List;

public interface RideReservationService {
    List<RideSearchResponse> searchRides(RideSearchRequest request);
    ReservationResponse reserveSeats(String rideUuid, ReserveSeatsRequest request);
    ConfirmationResponse confirmReservation(String reservationId);
    ReleaseResponse releaseReservation(String reservationId, String reason);
    ReleaseResponse expireReservation(String reservationId);
    ReservationResponse getReservationStatus(String reservationId);
}
