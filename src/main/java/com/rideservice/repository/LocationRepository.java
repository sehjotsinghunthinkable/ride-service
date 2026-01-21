package com.rideservice.repository;

import com.rideservice.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    @Query("Select l from Location l where l.name = :name")
    Optional<Location> findByName(String name);
}
