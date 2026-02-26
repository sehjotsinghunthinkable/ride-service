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

    Optional<RideReservation> findByReservationUuid(String bookingUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RideReservation b WHERE b.ride.id = :rideId AND b.status IN :statuses")
    List<RideReservation> findLockedByRideIdAndStatusIn(
            @Param("rideId") Long rideId,
            @Param("statuses") List<BookingStatus> statuses);

    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM RideReservation b " +
            "WHERE b.ride.id = :rideId " +
            "AND b.status IN ('RESERVED', 'CONFIRMED') " +
            "AND b.fromSequence < :toSequence " +
            "AND b.toSequence > :fromSequence")
    Integer getTotalBookedSeats(@Param("rideId") Long rideId,
                                @Param("fromSequence") Long fromSequence,
                                @Param("toSequence") Long toSequence);

    // pessimistic lock (for reservation)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM RideReservation b " +
            "WHERE b.ride.id = :rideId " +
            "AND b.status IN ('RESERVED', 'CONFIRMED') " +
            "AND b.fromSequence < :toSequence " +
            "AND b.toSequence > :fromSequence")
    Integer getTotalBookedSeatsWithLock(@Param("rideId") Long rideId,
                                        @Param("fromSequence") Long fromSequence,
                                        @Param("toSequence") Long toSequence);

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("SELECT b FROM RideReservation b " +
//            "WHERE b.status = 'RESERVED' " +
//            "AND b.expiresAt < :now " +
//            "ORDER BY b.expiresAt")
    @Query(value = "SELECT * FROM ride_reservation r " +
            "WHERE r.status = 'RESERVED' " +
            "AND r.expires_at < :now " +
            "ORDER BY r.expires_at " +
            "LIMIT 100 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<RideReservation> findAndLockExpiredReservations(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RideReservation b SET b.status = 'EXPIRED', b.version = b.version + 1 " +
            "WHERE b.status = 'RESERVED' AND b.expiresAt < :now")
    int bulkExpireReservations(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(b) > 0 FROM RideReservation b " +
            "WHERE b.ride.uuid = :rideUuid " +
            "AND b.status IN ('RESERVED', 'CONFIRMED')")
    boolean hasActiveBookings(@Param("rideUuid") String rideUuid);

    @Query("SELECT COUNT(b) FROM RideReservation b " +
            "WHERE b.ride.uuid = :rideUuid " +
            "AND b.status IN ('RESERVED', 'CONFIRMED')")
    int getActiveBookingCount(@Param("rideUuid") String rideUuid);

}
