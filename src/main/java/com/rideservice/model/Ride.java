package com.rideservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ride extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @ManyToOne
//    @JoinColumn(name = "source_location_id", referencedColumnName = "id")
//    private Location sourceLocation;
//
//    @ManyToOne
//    @JoinColumn(name = "destination_location_id", referencedColumnName = "id")
//    private Location destinationLocation;

    private LocalDateTime startTime;

//    private LocalDateTime endTime;

    private Long totalSeats;

    private Long driverId;

    private Long carId;

    @OneToMany(mappedBy = "ride",cascade = CascadeType.PERSIST)
    private List<RideStop> rideStops;

    @OneToMany(mappedBy = "ride",cascade = CascadeType.PERSIST)
    private List<RideSegmentSeat> segmentSeats;
}
