package com.rideservice.service.impl;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.dto.ride.response.RideDetailsResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.mapper.RideMapper;
import com.rideservice.model.Location;
import com.rideservice.model.Ride;
import com.rideservice.model.RideSegmentSeat;
import com.rideservice.model.RideStop;
import com.rideservice.repository.LocationRepository;
import com.rideservice.repository.RideRepository;
import com.rideservice.service.LocationService;
import com.rideservice.service.RideService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideServiceImpl implements RideService {
    private final RideRepository rideRepository;
    private final RideMapper rideMapper;
    private final LocationRepository locationRepository;
    private final LocationService locationService;

    @Override
    @Transactional
    public RideResponseDto saveRideDetails(RideCreationDto rideCreationDto) {
        Ride ride = rideMapper.toRideWithSegmentMapping(rideCreationDto);
        rideRepository.save(ride);
        return rideMapper.toRideResponseDto(ride);
    }

    @Override
    public List<RideDetailResponse> getAllRideDetails(Locations from, Locations to, Date date) {
        Location fromLocation = locationService.getLocation(from.toString());
        Location toLocation = locationService.getLocation(to.toString());
        if (fromLocation == null || toLocation == null) {
            throw new IllegalArgumentException("From or To location is null");
        }
        LocalDate localDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDateTime startOfDay = localDateTime.atStartOfDay();
        LocalDateTime endOfDay = localDateTime.plusDays(1).atStartOfDay();
        return rideRepository.getAllRideDetails(fromLocation.getId(), toLocation.getId(), startOfDay, endOfDay).stream().filter(e -> e.getAvailableSeats() > 0).toList();
    }

    @Override
    public RideDetailsResponse getRideDetails(String rideUuid, Locations source, Locations destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Source and destination must be provided");
        }
        Ride ride = rideRepository.findByUuid(rideUuid)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found with uuid " + rideUuid));
        List<RideStop> stops = ride.getRideStops();
        if (stops == null || stops.size() < 2) {
            throw new IllegalArgumentException("Ride stops not found");
        }
        stops.sort(Comparator.comparingLong(RideStop::getSequence));
        Location sourceLocation = locationService.getLocation(source.toString());
        Location destinationLocation = locationService.getLocation(destination.toString());
        int sourceIndex = -1;
        int destinationIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getStop().equals(sourceLocation)) sourceIndex = i;
            if (stops.get(i).getStop().equals(destinationLocation)) destinationIndex = i;
        }
        if (sourceIndex == -1 || destinationIndex == -1 || sourceIndex >= destinationIndex) {
            throw new IllegalArgumentException("Invalid source/destination order");
        }
        LocalDateTime pickupTime = ride.getStartTime();
        long price = 0;
        for (int i = 0; i <= sourceIndex; i++) {
            pickupTime = pickupTime.plusMinutes(stops.get(i).getDurationOffset());
        }
        LocalDateTime dropTime = pickupTime;
        for (int i = sourceIndex + 1; i <= destinationIndex; i++) {
            price += stops.get(i).getPrice();
            dropTime = dropTime.plusMinutes(stops.get(i).getDurationOffset());
        }
        long sourceSeq = stops.get(sourceIndex).getSequence();
        long destSeq = stops.get(destinationIndex).getSequence();
        long seats = ride.getSegmentSeats().stream()
                .filter(s -> s.getFromSequence() >= sourceSeq && s.getToSequence() <= destSeq)
                .mapToLong(RideSegmentSeat::getAvailableSeats)
                .min()
                .orElse(0);
        RideDetailsResponse response = new RideDetailsResponse();
        response.setRideUuid(ride.getUuid());
        response.setCarId(ride.getCarId());
        response.setDriverId(ride.getDriverId());
        response.setStartTime(pickupTime);
        response.setEndTime(dropTime);
        response.setPrice(price);
        response.setAvailableSeats(seats);
        response.setSource(source);
        response.setDestination(destination);
        return response;
    }

}
