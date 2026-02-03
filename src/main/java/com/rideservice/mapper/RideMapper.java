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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.rideservice.utils.AuditDetailUtil.addRideCreationDetails;
import static com.rideservice.utils.AuditDetailUtil.addRideSegmentCreationDetails;
import static com.rideservice.utils.AuditDetailUtil.addRideStopCreationDetails;

@Component
@RequiredArgsConstructor
public class RideMapper {
    private final LocationService locationService;

    public RideResponseDto toRideResponseDto(Ride ride) {
        RideResponseDto rideResponseDto = new RideResponseDto();
        rideResponseDto.setUuid(ride.getUuid());
        rideResponseDto.setStartTime(ride.getStartTime());
        rideResponseDto.setDriverId(ride.getDriverId());
        rideResponseDto.setCarId(ride.getCarId());
        List<StopsResponseDto> stopsResponseDto = toStopResponseDto(ride.getRideStops());
        rideResponseDto.setStops(stopsResponseDto);
        return rideResponseDto;
    }

    private List<StopsResponseDto> toStopResponseDto(List<RideStop> rideStops) {
        List<StopsResponseDto> stopsResponseDtos = new ArrayList<>();
        for (RideStop rideStop : rideStops) {
            StopsResponseDto stopsResponseDto = new StopsResponseDto();
            stopsResponseDto.setUuid(rideStop.getUuid());
            stopsResponseDto.setPrice(rideStop.getPrice());
            stopsResponseDto.setDurationOffset(rideStop.getDurationOffset());
            stopsResponseDto.setName(rideStop.getStop().getName());
            stopsResponseDto.setSequence(rideStop.getSequence());
            stopsResponseDtos.add(stopsResponseDto);
        }
        return stopsResponseDtos;
    }

    public Ride toRideWithSegmentMapping(RideCreationDto rideCreationDto) {
        Ride ride = new Ride();
        ride.setCarId(rideCreationDto.getCarId());
        ride.setDriverId(rideCreationDto.getDriverId());
        ride.setStartTime(rideCreationDto.getStartTime());
        ride.setTotalSeats(rideCreationDto.getTotalSeats());
        ride.setUuid(UUID.randomUUID().toString());
        addRideCreationDetails(ride);
        mapStopsAndSeatSegments(rideCreationDto, ride);
        return ride;
    }

    private void mapStopsAndSeatSegments(RideCreationDto rideCreationDto, Ride ride) {
        List<StopsDto> stopsDtos = rideCreationDto.getStops();
        List<RideStop> stops = toStop(stopsDtos, ride);
        ride.setRideStops(stops);
        List<RideSegmentSeat> rideSegmentSeats = new ArrayList<>();
        for (long i = 0; i < stops.size() - 1; i++) {
            RideSegmentSeat rideSegmentSeat = new RideSegmentSeat();
            rideSegmentSeat.setAvailableSeats(rideCreationDto.getTotalSeats());
            rideSegmentSeat.setFromSequence(i + 1);
            rideSegmentSeat.setToSequence(i + 2);
            rideSegmentSeat.setUuid(UUID.randomUUID().toString());
            rideSegmentSeat.setRide(ride);
            addRideSegmentCreationDetails(rideSegmentSeat);
            rideSegmentSeats.add(rideSegmentSeat);
        }
        ride.setSegmentSeats(rideSegmentSeats);
    }

    public List<RideStop> toStop(List<StopsDto> stopsDtos, Ride ride) {
        List<RideStop> rideStops = new ArrayList<>();
        long sequence = 1;
        for (StopsDto stopsDto : stopsDtos) {
            RideStop rideStop = new RideStop();
            Location location = locationService.getLocation(stopsDto.getName().toString());
            rideStop.setStop(location);
            rideStop.setSequence(sequence++);
            rideStop.setPrice(stopsDto.getPrice());
            rideStop.setDurationOffset(stopsDto.getDurationOffset());
            rideStop.setRide(ride);
            rideStop.setUuid(UUID.randomUUID().toString());
            rideStops.add(rideStop);
            addRideStopCreationDetails(rideStop);
        }
        return rideStops;
    }
}
