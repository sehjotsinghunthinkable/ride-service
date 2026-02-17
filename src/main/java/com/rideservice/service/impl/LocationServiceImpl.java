package com.rideservice.service.impl;

import com.rideservice.model.Location;
import com.rideservice.repository.LocationRepository;
import com.rideservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {
    private final LocationRepository locationRepository;

    @Override
    public Location getLocation(String name) {
        String key = name.trim().toLowerCase();
        return locationRepository.findByName(key)
                .orElseGet(() -> {
                    Location location = new Location();
                    location.setName(key);
                    location.setCreatedAt(LocalDateTime.now());
                    return locationRepository.save(location);
                });
    }
}

