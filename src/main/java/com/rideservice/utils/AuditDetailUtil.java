package com.rideservice.utils;

import com.rideservice.model.Ride;
import com.rideservice.model.RideReservation;
import com.rideservice.model.RideStop;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditDetailUtil {

    public static void addRideCreationDetails(Ride ride) {
        ride.setCreatedAt(LocalDateTime.now());
//        ride.setCreatedBy(ride.getCreatedBy());
    }

    public static void addRideBookingCreationDetails(RideReservation rideReservation) {
        rideReservation.setCreatedAt(LocalDateTime.now());
//        rideSegmentSeat.setCreatedBy(rideSegmentSeat.getCreatedBy());
    }

    public static void addRideStopCreationDetails(RideStop rideStop) {
        rideStop.setCreatedAt(LocalDateTime.now());
//        rideStop.setCreatedBy(rideStop.getCreatedBy());
    }
}
