package com.rideservice.controller;

import com.rideservice.constants.enums.Locations;
import com.rideservice.dto.ride.request.RideCreationDto;
import com.rideservice.dto.ride.response.RideDetailResponse;
import com.rideservice.dto.ride.response.RideDetailsResponse;
import com.rideservice.dto.ride.response.RideResponseDto;
import com.rideservice.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public List<RideDetailResponse> getAllRideDetails(
            @RequestParam Locations source,
            @RequestParam Locations destination,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd")
            Date departure
            ){
        return rideService.getAllRideDetails(source, destination, departure);
    }

    @GetMapping("/{rideId}")
    public RideDetailsResponse getRideDetails(@PathVariable("rideId") String rideUuid,
                                              @RequestParam Locations source,
                                              @RequestParam Locations destination){
        return rideService.getRideDetails(rideUuid, source, destination);
    }
}
