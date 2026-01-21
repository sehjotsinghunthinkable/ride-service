package com.rideservice.controller;

import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.ListingResponseDto;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/ride")
@RequiredArgsConstructor
public class RideController {
    private final RideService rideService;

    @PostMapping
    public RideResponseDto saveRideDetails(@RequestBody RideCreationDto rideCreationDto){
        return rideService.saveRideDetails(rideCreationDto);
    }

    @GetMapping("/all")
    public List<ListingResponseDto> getAllRideDetails(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd")
            Date departure
            ){
        return rideService.getAllRideDetails(source, destination, departure);
    }
}
