package com.rideservice.repository;

import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    @Query(value = """
            SELECT
              r.uuid AS rideUuid,
              price_sum.price AS price,
              MIN(COALESCE(s.available_seats, 0)) AS availableSeats,
              :from AS from,
              :to AS to,
              r.driver_id AS driverId,
              r.car_id AS carId,
            
              r.start_time +
              (
                SELECT SUM(rs.duration_offset)
                FROM ride_stop rs
                WHERE rs.ride_id = r.id
                  AND rs.sequence <= sa.sequence
              ) * INTERVAL '1 minute' AS startTime
            
            FROM ride r
            
            JOIN ride_stop sa
              ON sa.ride_id = r.id
             AND sa.stop_id = :from
            
            JOIN ride_stop sb
              ON sb.ride_id = r.id
             AND sb.stop_id = :to
            
            JOIN LATERAL (
              SELECT SUM(rs.price) AS price
              FROM ride_stop rs
              WHERE rs.ride_id = r.id
                AND rs.sequence > sa.sequence
                AND rs.sequence <= sb.sequence
            ) price_sum ON true
            
            LEFT JOIN ride_segment_seat s
              ON s.ride_id = r.id
             AND s.from_sequence >= sa.sequence
             AND s.to_sequence <= sb.sequence
            
            WHERE sa.sequence < sb.sequence
            
              -- pickup in selected day window
              AND r.start_time +
                  (
                    SELECT SUM(rs.duration_offset)
                    FROM ride_stop rs
                    WHERE rs.ride_id = r.id
                      AND rs.sequence <= sa.sequence
                  ) * INTERVAL '1 minute' >= :dayStart
            
              AND r.start_time +
                  (
                    SELECT SUM(rs.duration_offset)
                    FROM ride_stop rs
                    WHERE rs.ride_id = r.id
                      AND rs.sequence <= sa.sequence
                  ) * INTERVAL '1 minute' < :dayEnd
            
              -- pickup must be in future
              AND r.start_time +
                  (
                    SELECT SUM(rs.duration_offset)
                    FROM ride_stop rs
                    WHERE rs.ride_id = r.id
                      AND rs.sequence <= sa.sequence
                  ) * INTERVAL '1 minute' >= NOW()
            
            GROUP BY
              r.id,
              price_sum.price,
              r.driver_id,
              r.car_id,
              r.start_time,
              sa.sequence;
            """, nativeQuery = true)
    List<RideDetailResponse> getAllRideDetails(Long from, Long to, LocalDateTime dayStart, LocalDateTime dayEnd);

    Optional<Ride> findByUuid(String rideUuid);
}
