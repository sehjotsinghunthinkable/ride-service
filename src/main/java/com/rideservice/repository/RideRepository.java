package com.rideservice.repository;

import com.rideservice.dto.ride.response.ListingResponseDto;
import com.rideservice.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    @Query(value = """
            SELECT
              r.id AS rideId,
              price_sum.price AS price,
              MIN(COALESCE(s.available_seats, 0)) AS availableSeats,
              :from AS from,
              :to AS to,
              r.driver_id AS driverId,
              r.car_id AS carId,
              r.start_time + (sa.duration_offset || ' minutes')::interval AS time
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
            
            WHERE r.status <> 'COMPLETED'
              AND sa.sequence < sb.sequence
              AND r.start_time
                    + (sa.duration_offset || ' minutes')::interval >= :dayStart
              AND r.start_time
                    + (sa.duration_offset || ' minutes')::interval < :dayEnd
              AND r.start_time
                    + (sa.duration_offset || ' minutes')::interval >= NOW()
            
            GROUP BY
              r.id,
              price_sum.price,
              r.driver_id,
              r.car_id,
              r.start_time,
              sa.duration_offset;
            
            """, nativeQuery = true)
    List<ListingResponseDto> getAllRideDetails(Long from, Long to, LocalDateTime dayStart, LocalDateTime dayEnd);
}
