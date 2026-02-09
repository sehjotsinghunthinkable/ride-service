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

    public RideResponseDto toRideResponseDto(Ride ride) {
        RideResponseDto rideResponseDto = new RideResponseDto();
        rideResponseDto.setUuid(ride.getUuid());
        rideResponseDto.setStartTime(ride.getStartTime());
        rideResponseDto.setDriverId(ride.getDriverId());
        rideResponseDto.setCarId(ride.getCarId());
        rideResponseDto.setTotalSeats(ride.getTotalSeats());

        // Map stops
        List<StopsResponseDto> stopsResponse = ride.getRideStops().stream()
                .sorted(Comparator.comparingLong(RideStop::getSequence))
                .map(this::toStopResponseDto)
                .collect(Collectors.toList());

        rideResponseDto.setStops(stopsResponse);
        return rideResponseDto;
    }

    private StopsResponseDto toStopResponseDto(RideStop rideStop) {
        StopsResponseDto stopsResponseDto = new StopsResponseDto();
        stopsResponseDto.setUuid(rideStop.getUuid());
        stopsResponseDto.setPrice(rideStop.getPrice());
        stopsResponseDto.setDurationOffset(rideStop.getDurationOffset());
        stopsResponseDto.setName(rideStop.getStop().getName());
        stopsResponseDto.setSequence(rideStop.getSequence());
        return stopsResponseDto;
    }


    public Ride toRideWithSegmentMapping(RideCreationDto rideCreationDto) {
        // Validate minimum stops
        if (rideCreationDto.getStops() == null || rideCreationDto.getStops().size() < 2) {
            throw new IllegalArgumentException("At least 2 stops are required");
        }

        //1. create ride
        Ride ride = new Ride();
        ride.setCarId(rideCreationDto.getCarId());
        ride.setDriverId(rideCreationDto.getDriverId());
        ride.setStartTime(rideCreationDto.getStartTime());
        ride.setTotalSeats(rideCreationDto.getTotalSeats());
        ride.setUuid(UUID.randomUUID().toString());
        addRideCreationDetails(ride);

        // 2. Create stops with cumulative values
        List<RideStop> stops = createStopsWithCumulativeValues(rideCreationDto, ride);
        ride.setRideStops(stops);

        // 3. Create all segment combinations
        List<RideSegmentSeat> rideSegmentSeats = createAllSegmentSeats(rideCreationDto, ride, stops.size());
        ride.setSegmentSeats(rideSegmentSeats);

        log.info("Created ride with {} stops and {} segments",
                stops.size(), rideSegmentSeats.size());
        return ride;
    }

    private List<RideSegmentSeat> createAllSegmentSeats(RideCreationDto rideCreationDto,
                                                        Ride ride, int stopCount) {
        List<RideSegmentSeat> rideSegmentSeats = new ArrayList<>();

        // Create all combinations (i, j) where i < j
        for (int i = 0; i < stopCount; i++) {
            for (int j = i + 1; j < stopCount; j++) {
                RideSegmentSeat rideSegmentSeat = new RideSegmentSeat();
                rideSegmentSeat.setAvailableSeats(rideCreationDto.getTotalSeats());
                rideSegmentSeat.setFromSequence((long) i);  // Start at 0
                rideSegmentSeat.setToSequence((long) j);    // Start at 0
                rideSegmentSeat.setUuid(UUID.randomUUID().toString());
                rideSegmentSeat.setRide(ride);
                addRideSegmentCreationDetails(rideSegmentSeat);
                rideSegmentSeats.add(rideSegmentSeat);
            }
        }
        log.debug("Created {} segment seats for {} stops", rideSegmentSeats.size(), stopCount);
        return rideSegmentSeats;
    }

    private List<RideStop> createStopsWithCumulativeValues(RideCreationDto rideCreationDto, Ride ride) {
        List<RideStop> rideStops = new ArrayList<>();
        List<StopsDto> stopsDtos = rideCreationDto.getStops();

        long cumulativeDuration = 0;
        long cumulativePrice = 0;

        for (int i = 0; i < stopsDtos.size(); i++) {
            StopsDto stopsDto = stopsDtos.get(i);

            if (i == 0) {
                // First stop must have durationOffset = 0 and price = 0
                if (stopsDto.getDurationOffset() != 0) {
                    throw new IllegalArgumentException("First stop must have durationOffset = 0");
                }
                if (stopsDto.getPrice() != 0) {
                    throw new IllegalArgumentException("First stop must have price = 0");
                }
            }

            Location location = locationService.getLocation(stopsDto.getName().toString());

            // Create stop
            RideStop rideStop = new RideStop();
            rideStop.setStop(location);
            rideStop.setSequence((long) i);  // Start at 0

            // Set cumulative values
            if (i == 0) {
                rideStop.setDurationOffset(0L);
                rideStop.setPrice(0L);
            } else {
                // Validate positive values
                if (stopsDto.getDurationOffset() <= 0) {
                    throw new IllegalArgumentException(
                            "Duration offset must be positive for stop: " + stopsDto.getName());
                }
                if (stopsDto.getPrice() <= 0) {
                    throw new IllegalArgumentException(
                            "Price must be positive for stop: " + stopsDto.getName());
                }
                cumulativeDuration += stopsDto.getDurationOffset();  // duration from prev
                cumulativePrice += stopsDto.getPrice();              // price from prev

                rideStop.setDurationOffset(cumulativeDuration);
                rideStop.setPrice(cumulativePrice);
            }

            rideStop.setRide(ride);
            rideStop.setUuid(UUID.randomUUID().toString());
            rideStops.add(rideStop);
            addRideStopCreationDetails(rideStop);
        }
        return rideStops;
    }
}
