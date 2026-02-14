package com.rideservice.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideCacheService {

    // For simple values (seat counts) - StringRedisTemplate
    private final StringRedisTemplate stringRedisTemplate;

    // For complex objects - RedisTemplate with RedisSerializer.json()
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SEAT_CACHE_KEY = "ride:seats:%s:%d:%d";
    private static final long SEAT_CACHE_TTL = 30; // 30 seconds

    public void cacheSeats(String rideUuid, Long fromSeq, Long toSeq, Integer availableSeats) {
        String key = String.format(SEAT_CACHE_KEY, rideUuid, fromSeq, toSeq);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(availableSeats), SEAT_CACHE_TTL, TimeUnit.SECONDS);
        log.debug("Cached seats for {}: {}-{} = {}", rideUuid, fromSeq, toSeq, availableSeats);
    }

    public Integer getSeats(String rideUuid, Long fromSeq, Long toSeq) {
        String key = String.format(SEAT_CACHE_KEY, rideUuid, fromSeq, toSeq);
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value != null) {
            log.debug("Cache hit for {}: {}-{} = {}", rideUuid, fromSeq, toSeq, value);
            return Integer.parseInt(value);
        }
        return null;
    }

    public void updateSeats(String rideUuid, Long fromSeq, Long toSeq, int delta) {
        String key = String.format(SEAT_CACHE_KEY, rideUuid, fromSeq, toSeq);
        Long newValue = stringRedisTemplate.opsForValue().increment(key, delta);
        log.debug("Updated seats for {}: {}-{} by {} = {}", rideUuid, fromSeq, toSeq, delta, newValue);
    }

    public void invalidateRideSeats(String rideUuid) {
        String pattern = String.format("ride:seats:%s:*", rideUuid);
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("Invalidated {} seat cache entries for ride: {}", keys.size(), rideUuid);
        }
    }
}