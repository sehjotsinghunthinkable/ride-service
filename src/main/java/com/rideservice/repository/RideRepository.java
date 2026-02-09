package com.rideservice.repository;

import com.rideservice.dto.ride.response.RideDetailResponse;
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

    @Query("SELECT DISTINCT r FROM Ride r " +
            "LEFT JOIN FETCH r.rideStops rs " +
            "LEFT JOIN FETCH rs.stop " +
            "WHERE r.uuid = :uuid AND r.isDeleted = false")
    Optional<Ride> findByUuidWithStops(@Param("uuid") String uuid);

    @Query("SELECT ss FROM RideSegmentSeat ss " +
            "WHERE ss.ride.uuid = :rideUuid " +
            "AND ss.isDeleted = false")
    List<RideSegmentSeat> findSegmentSeatsByRideUuid(@Param("rideUuid") String rideUuid);
}
