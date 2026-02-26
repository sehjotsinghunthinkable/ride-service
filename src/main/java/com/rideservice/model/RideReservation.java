package com.rideservice.model;

import com.rideservice.constants.enums.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ride_reservation",
        indexes = {
                @Index(name = "idx_ride_bookings_ride_id", columnList = "ride_id"),
                @Index(name = "idx_ride_bookings_status", columnList = "status"),
                @Index(name = "idx_ride_bookings_expires", columnList = "expires_at"),
                @Index(name = "idx_ride_bookings_uuid", columnList = "booking_uuid"),
                @Index(name = "idx_expiry", columnList = "status, expires_at")
        })
public class RideReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_uuid", nullable = false, unique = true)
    private String reservationUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "from_sequence", nullable = false)
    private Long fromSequence;

    @Column(name = "to_sequence", nullable = false)
    private Long toSequence;

    @Column(name = "seats_booked", nullable = false)
    private Integer seatsBooked;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    @Column(name = "version")
    private Integer version = 0;  // optimistic locking

    @PrePersist
    public void prePersist() {
        if (reservationUuid == null) {
            reservationUuid = UUID.randomUUID().toString();
        }
        if (status == BookingStatus.RESERVED && expiresAt == null) {
            expiresAt = LocalDateTime.now().plusMinutes(10);
        }
    }
}