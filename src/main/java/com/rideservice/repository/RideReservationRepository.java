package com.rideservice.repository;

import com.rideservice.constants.enums.BookingStatus;
import com.rideservice.model.RideReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RideReservationRepository extends JpaRepository<RideReservation, Long> {

    // Find by UUID (for confirmation/cancellation)
    Optional<RideReservation> findByReservationUuid(String bookingUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RideReservation b WHERE b.ride.id = :rideId AND b.status IN :statuses")
    List<RideReservation> findLockedByRideIdAndStatusIn(
            @Param("rideId") Long rideId,
            @Param("statuses") List<BookingStatus> statuses);

    // Calculate total seats booked for a specific segment (including RESERVED)
    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM RideReservation b " +
            "WHERE b.ride.id = :rideId " +
            "AND b.status IN ('RESERVED', 'CONFIRMED') " +
            "AND b.fromSequence < :toSequence " +
            "AND b.toSequence > :fromSequence")
    Integer getTotalBookedSeats(@Param("rideId") Long rideId,
                                @Param("fromSequence") Long fromSequence,
                                @Param("toSequence") Long toSequence);

    // Same query but with pessimistic lock (for reservation)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM RideReservation b " +
            "WHERE b.ride.id = :rideId " +
            "AND b.status IN ('RESERVED', 'CONFIRMED') " +
            "AND b.fromSequence < :toSequence " +
            "AND b.toSequence > :fromSequence")
    Integer getTotalBookedSeatsWithLock(@Param("rideId") Long rideId,
                                        @Param("fromSequence") Long fromSequence,
                                        @Param("toSequence") Long toSequence);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RideReservation b " +
            "WHERE b.status = 'RESERVED' " +
            "AND b.expiresAt < :now " +
            "ORDER BY b.expiresAt")
    List<RideReservation> findAndLockExpiredReservations(@Param("now") LocalDateTime now);

    // Bulk update expired reservations
    @Modifying
    @Query("UPDATE RideReservation b SET b.status = 'EXPIRED', b.version = b.version + 1 " +
            "WHERE b.status = 'RESERVED' AND b.expiresAt < :now")
    int bulkExpireReservations(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(b) > 0 FROM RideReservation b " +
            "WHERE b.ride.uuid = :rideUuid " +
            "AND b.status IN ('RESERVED', 'CONFIRMED')")
    boolean hasActiveBookings(@Param("rideUuid") String rideUuid);

    // Get booking count for a ride
    @Query("SELECT COUNT(b) FROM RideReservation b " +
            "WHERE b.ride.uuid = :rideUuid " +
            "AND b.status IN ('RESERVED', 'CONFIRMED')")
    int getActiveBookingCount(@Param("rideUuid") String rideUuid);

}
