package com.rideservice.model;

public enum BookingStatus {
    RESERVED,    // Held for 10 minutes during payment
    CONFIRMED,   // Payment successful, booking finalized
    CANCELLED,   // User cancelled or payment failed
    EXPIRED      // Reservation timed out
}
