package com.rideservice.mapper;

import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.request.StopsDto;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.dto.ride.response.StopsResponseDto;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.model.RideSegmentSeat;
import com.rideservice.model.RideStop;
import com.rideservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.rideservice.utils.AuditDetailUtil.addRideCreationDetails;
import static com.rideservice.utils.AuditDetailUtil.addRideSegmentCreationDetails;
import static com.rideservice.utils.AuditDetailUtil.addRideStopCreationDetails;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideMapper {
    private final LocationService locationService;

    public Ride toRide(RideCreationDto rideCreationDto) {
        // Validate minimum stops
        if (rideCreationDto.getStops() == null || rideCreationDto.getStops().size() < 2) {
            throw new IllegalArgumentException("At least 2 stops are required");
        }

        // 1. Create ride
        Ride ride = new Ride();
        ride.setCarId(rideCreationDto.getCarId());
        ride.setDriverId(rideCreationDto.getDriverId());
        ride.setStartTime(rideCreationDto.getStartTime());
        ride.setTotalSeats(rideCreationDto.getTotalSeats());
        ride.setUuid(UUID.randomUUID().toString());

        // 2. Create stops with cumulative values
        List<RideStop> stops = createStopsWithCumulativeValues(rideCreationDto, ride);
        ride.setRideStops(stops);

        log.info("Created ride with {} stops",
                stops.size());
        return ride;
    }

    private List<RideStop> createStopsWithCumulativeValues(RideCreationDto rideCreationDto, Ride ride) {
        List<RideStop> rideStops = new ArrayList<>();
        List<StopsDto> stopsDtos = rideCreationDto.getStops();

        long cumulativeDuration = 0;
        long cumulativePrice = 0;

        for (int i = 0; i < stopsDtos.size(); i++) {
            StopsDto stopsDto = stopsDtos.get(i);

            if (i == 0) {
                // First stop validation
                if (stopsDto.getDurationOffset() != 0) {
                    throw new IllegalArgumentException("First stop must have durationOffset = 0");
                }
                if (stopsDto.getPrice() != 0) {
                    throw new IllegalArgumentException("First stop must have price = 0");
                }
            } else {
                // Validate positive values for non-first stops
                if (stopsDto.getDurationOffset() <= 0) {
                    throw new IllegalArgumentException(
                            "Duration offset must be positive for stop: " + stopsDto.getName());
                }
                if (stopsDto.getPrice() <= 0) {
                    throw new IllegalArgumentException(
                            "Price must be positive for stop: " + stopsDto.getName());
                }
            }

            Location location = locationService.getLocation(stopsDto.getName().toString());

            // Create stop
            RideStop rideStop = new RideStop();
            rideStop.setStop(location);
            rideStop.setSequence((long) i);

            // Set cumulative values
            if (i == 0) {
                rideStop.setDurationOffset(0L);
                rideStop.setPrice(0L);
            } else {
                cumulativeDuration += stopsDto.getDurationOffset();
                cumulativePrice += stopsDto.getPrice();

                rideStop.setDurationOffset(cumulativeDuration);
                rideStop.setPrice(cumulativePrice);
            }

            rideStop.setRide(ride);
            rideStop.setUuid(UUID.randomUUID().toString());
            rideStops.add(rideStop);
        }

        // Validate that stops are in chronological order
        validateStopsOrder(rideStops);

        return rideStops;
    }

    private void validateStopsOrder(List<RideStop> stops) {
        for (int i = 1; i < stops.size(); i++) {
            RideStop prev = stops.get(i-1);
            RideStop current = stops.get(i);

            if (prev.getDurationOffset() >= current.getDurationOffset()) {
                throw new IllegalArgumentException(
                        String.format("Stops must be in chronological order. Stop %d (offset=%d) must come before stop %d (offset=%d)",
                                i-1, prev.getDurationOffset(), i, current.getDurationOffset()));
            }

            if (prev.getPrice() >= current.getPrice()) {
                throw new IllegalArgumentException(
                        String.format("Prices must increase along the route. Stop %d (price=%d) to stop %d (price=%d)",
                                i-1, prev.getPrice(), i, current.getPrice()));
            }
        }
    }

    public RideResponseDto toRideResponseDto(Ride ride) {
        if (ride == null) return null;

        RideResponseDto dto = new RideResponseDto();
        dto.setRideUuid(ride.getUuid());
        dto.setDriverId(ride.getDriverId());
        dto.setCarId(ride.getCarId());
        dto.setStartTime(ride.getStartTime());
        dto.setTotalSeats(ride.getTotalSeats());
        if (ride.getRideStops() != null) {
            dto.setStopCount(ride.getRideStops().size());
        }

        return dto;
    }
}
