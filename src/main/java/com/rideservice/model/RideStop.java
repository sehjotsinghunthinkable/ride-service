package com.rideservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RideStop extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private Location stop;

    @Column(name= "sequence", nullable = false)
    private Long sequence;

    @Column(name= "price", nullable = false)
    private Long price; //price from ride start to this stop

    @Column(name= "duration_offset", nullable = false)
    private Long durationOffset; //mins from ride start

    @PrePersist
    @PreUpdate
    private void validate() {
        if (sequence < 0) {
            throw new IllegalArgumentException("Sequence cannot be negative");
        }
        if (durationOffset < 0) {
            throw new IllegalArgumentException("Duration offset cannot be negative");
        }
        if (sequence == 0 && price != 0) {
            throw new IllegalArgumentException("First stop must have price 0");
        }
    }

}

