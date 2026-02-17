package com.rideservice.repository;

import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.dto.ride.response.RideSearchProjection;
import com.rideservice.model.Ride;
import com.rideservice.model.RideSegmentSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    @Query(value = """
        SELECT DISTINCT
            r.uuid as rideUuid,
            r.driver_id as driverId,
            r.car_id as carId,
            
            -- Source and destination info for display
            :fromLocationName as from,
            :toLocationName as to,
            
            -- Pickup time: ride start + source stop's CUMULATIVE duration offset
            r.start_time + (src.duration_offset * INTERVAL '1 minute') as startTime,
            
            -- Drop time: ride start + destination stop's CUMULATIVE duration offset  
            r.start_time + (dest.duration_offset * INTERVAL '1 minute') as endTime,
            
            -- Price: dest CUMULATIVE price - source CUMULATIVE price
            dest.price - src.price as price,
            
            -- Available seats: minimum across ALL OVERLAPPING segments
            (
                SELECT COALESCE(MIN(rss.available_seats), r.total_seats)
                FROM ride_segment_seat rss
                WHERE rss.ride_id = r.id
                  AND rss.is_deleted = false
                  AND rss.from_sequence < dest.sequence    -- Segment starts BEFORE drop point
                  AND rss.to_sequence > src.sequence       -- Segment ends AFTER pickup point
            ) as availableSeats
            
        FROM ride r
        
        -- Source stop (where passenger gets ON)
        JOIN ride_stop src ON r.id = src.ride_id
            AND src.stop_id = :fromLocationId
            AND src.is_deleted = false
            
        -- Destination stop (where passenger gets OFF)
        JOIN ride_stop dest ON r.id = dest.ride_id
            AND dest.stop_id = :toLocationId
            AND dest.is_deleted = false
            AND dest.sequence > src.sequence  -- Destination must come after source
            
        WHERE r.is_deleted = false
          AND r.start_time >= :dayStart
          AND r.start_time < :dayEnd
          
          -- Only show rides where pickup time is in future (with buffer)
          AND (r.start_time + (src.duration_offset * INTERVAL '1 minute')) >= NOW()
    
        ORDER BY startTime
        LIMIT :limit
        """, nativeQuery = true)
    List<RideDetailResponse> getAllRideDetails(
            @Param("fromLocationId") Long fromLocationId,
            @Param("fromLocationName") String fromLocationName,
            @Param("toLocationId") Long toLocationId,
            @Param("toLocationName") String toLocationName,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("limit") Integer limit);

    Optional<Ride> findByUuid(String rideUuid);

    // Check if ride has any bookings (RESERVED or CONFIRMED)
    @Query("SELECT COUNT(b) > 0 FROM RideBooking b " +
            "WHERE b.ride.uuid = :rideUuid " +
            "AND b.status IN ('RESERVED', 'CONFIRMED')")
    boolean hasActiveBookings(@Param("rideUuid") String rideUuid);

    // Get booking count for a ride
    @Query("SELECT COUNT(b) FROM RideBooking b " +
            "WHERE b.ride.uuid = :rideUuid " +
            "AND b.status IN ('RESERVED', 'CONFIRMED')")
    int getActiveBookingCount(@Param("rideUuid") String rideUuid);

    @Query("SELECT DISTINCT r FROM Ride r " +
            "LEFT JOIN FETCH r.rideStops rs " +
            "LEFT JOIN FETCH rs.stop " +
            "WHERE r.uuid = :uuid AND r.isDeleted = false")
    Optional<Ride> findByUuidWithStops(@Param("uuid") String uuid);

    @Query(value = """
        WITH ride_candidate AS (
            SELECT
                r.uuid AS rideUuid,
                srcLoc.name AS fromStop,
                destLoc.name AS toStop,
                r.start_time + (src.duration_offset * INTERVAL '1 minute') AS pickupTime,
                r.start_time + (dest.duration_offset * INTERVAL '1 minute') AS dropTime,
                (dest.price - src.price) AS price,
                r.driver_id AS driverId,
                r.car_id AS carId,
                r.id AS rideId,
                r.total_seats AS totalSeats,
                src.sequence AS fromSequence,
                dest.sequence AS toSequence
            FROM ride r

            JOIN ride_stop src
                ON r.id = src.ride_id
                AND src.is_deleted = false

            JOIN location srcLoc
                ON src.stop_id = srcLoc.id
                AND LOWER(srcLoc.name) = LOWER(:fromLocationName)

            JOIN ride_stop dest
                ON r.id = dest.ride_id
                AND dest.is_deleted = false
                AND dest.sequence > src.sequence

            JOIN location destLoc
                ON dest.stop_id = destLoc.id
                AND LOWER(destLoc.name) = LOWER(:toLocationName)

            WHERE r.is_deleted = false
              AND r.uuid = :rideUuid
        )

        SELECT
            rc.rideUuid,
            rc.fromStop,
            rc.toStop,
            rc.pickupTime,
            rc.dropTime,
            rc.price,
            (rc.totalSeats - COALESCE(bsum.reservedSeats, 0)) AS availableSeats,
            rc.driverId,
            rc.carId

        FROM ride_candidate rc

        LEFT JOIN LATERAL (
            SELECT SUM(b.seats_booked) AS reservedSeats
            FROM ride_bookings b
            WHERE b.ride_id = rc.rideId
              AND b.status IN ('RESERVED', 'CONFIRMED')
              AND b.from_sequence < rc.toSequence
              AND b.to_sequence > rc.fromSequence
        ) bsum ON true

        WHERE (rc.totalSeats - COALESCE(bsum.reservedSeats, 0)) > 0
        """,
            nativeQuery = true)
    RideSearchProjection getRideDetails(
            @Param("rideUuid") String rideUuid,
            @Param("fromLocationName") String fromLocationName,
            @Param("toLocationName") String toLocationName
    );

    @Query(value = """

            WITH ride_candidates AS (
            SELECT DISTINCT
                r.uuid as rideUuid,
                :fromLocationName as fromStop,
                :toLocationName as toStop,
                r.start_time + (src.duration_offset * INTERVAL '1 minute') as pickupTime,
                r.start_time + (dest.duration_offset * INTERVAL '1 minute') as dropTime,
                (dest.price - src.price) as price,
                r.driver_id as driverId,
                r.car_id as carId,
                r.id as rideId,
                r.total_seats as totalSeats,
                src.sequence as fromSequence,
                dest.sequence as toSequence
            FROM ride r
            JOIN ride_stop src ON r.id = src.ride_id
                AND src.stop_id = :fromLocationId
                AND src.is_deleted = false
            JOIN ride_stop dest ON r.id = dest.ride_id
                AND dest.stop_id = :toLocationId
                AND dest.is_deleted = false
                AND dest.sequence > src.sequence
            WHERE r.is_deleted = false
                AND r.start_time >= :dayStart
                AND r.start_time < :dayEnd
                AND (r.start_time + (src.duration_offset * INTERVAL '1 minute')) >= NOW()
        )
        SELECT 
            rc.rideUuid as rideUuid,
            rc.fromStop as fromStop,
            rc.toStop as toStop,
            rc.pickupTime as pickupTime,
            rc.dropTime as dropTime,
            rc.price as price,
            (rc.totalSeats - COALESCE((
                SELECT SUM(b.seats_booked)
                FROM ride_bookings b
                WHERE b.ride_id = rc.rideId
                    AND b.status IN ('RESERVED', 'CONFIRMED')
                    AND b.from_sequence < rc.toSequence
                    AND b.to_sequence > rc.fromSequence
            ), 0)) as availableSeats,
            rc.driverId as driverId,
            rc.carId as carId
        FROM ride_candidates rc
        WHERE (rc.totalSeats - COALESCE((
                SELECT SUM(b.seats_booked)
                FROM ride_bookings b
                WHERE b.ride_id = rc.rideId
                    AND b.status IN ('RESERVED', 'CONFIRMED')
                    AND b.from_sequence < rc.toSequence
                    AND b.to_sequence > rc.fromSequence
            ), 0)) > 0
        ORDER BY rc.pickupTime
        LIMIT :limit
        """, nativeQuery = true)
    List<RideSearchProjection> searchAvailableRides(
            @Param("fromLocationId") Long fromLocationId,
            @Param("fromLocationName") String fromLocationName,
            @Param("toLocationId") Long toLocationId,
            @Param("toLocationName") String toLocationName,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("limit") Integer limit);

    @Query(value = """

            WITH ride_candidates AS (
            SELECT DISTINCT
                r.uuid AS rideUuid,
                :fromLocationName AS fromStop,
                :toLocationName AS toStop,
                r.start_time + (src.duration_offset * INTERVAL '1 minute') AS pickupTime,
                r.start_time + (dest.duration_offset * INTERVAL '1 minute') AS dropTime,
                (dest.price - src.price) AS price,
                r.driver_id AS driverId,
                r.car_id AS carId,
                r.id AS rideId,
                r.total_seats AS totalSeats,
                src.sequence AS fromSequence,
                dest.sequence AS toSequence
            FROM ride r
            JOIN ride_stop src\s
                ON r.id = src.ride_id
                AND src.stop_id = :fromLocationId
                AND src.is_deleted = false
            JOIN ride_stop dest\s
                ON r.id = dest.ride_id
                AND dest.stop_id = :toLocationId
                AND dest.is_deleted = false
                AND dest.sequence > src.sequence
            WHERE r.is_deleted = false
                AND r.start_time >= :dayStart
                AND r.start_time < :dayEnd
                AND (r.start_time + (src.duration_offset * INTERVAL '1 minute')) >= NOW()
        )
        
        SELECT
            rc.rideUuid,
            rc.fromStop,
            rc.toStop,
            rc.pickupTime,
            rc.dropTime,
            rc.price,
            (rc.totalSeats - COALESCE(bsum.reservedSeats, 0)) AS availableSeats,
            rc.driverId,
            rc.carId
        
        FROM ride_candidates rc
        
        LEFT JOIN LATERAL (
            SELECT SUM(b.seats_booked) AS reservedSeats
            FROM ride_bookings b
            WHERE b.ride_id = rc.rideId
              AND b.status IN ('RESERVED', 'CONFIRMED')
              AND b.from_sequence < rc.toSequence
              AND b.to_sequence > rc.fromSequence
        ) bsum ON true
        
        WHERE (rc.totalSeats - COALESCE(bsum.reservedSeats, 0)) > 0
        
        ORDER BY rc.pickupTime
        LIMIT :limit;
        """, nativeQuery = true)
    List<RideSearchProjection> searchAvailableRidesV2(
            @Param("fromLocationId") Long fromLocationId,
            @Param("fromLocationName") String fromLocationName,
            @Param("toLocationId") Long toLocationId,
            @Param("toLocationName") String toLocationName,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("limit") Integer limit);

}
