package com.rideservice.scheduler;

import com.rideservice.constants.enums.BookingStatus;
import com.rideservice.model.RideReservation;
import com.rideservice.repository.RideReservationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {
    private final RideReservationRepository rideReservationRepository;

    /**
     * Runs every minute to expire reservations that have passed their expiry time
     */
    @Scheduled(fixedDelay = 60000) // 60,000 ms = 1 minute
    @Transactional
    public void expireReservations() {
        log.debug("Running reservation expiry job at {}", LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();

        // Find all expired reservations
        List<RideReservation> expiredReservations = rideReservationRepository
                .findAndLockExpiredReservations(now);

        if (expiredReservations.isEmpty()) {
            log.debug("No expired reservations found");
            return;
        }

        log.info("Found {} expired reservations to process", expiredReservations.size());

        int expiredCount = 0;
        int failedCount = 0;

        for (RideReservation booking : expiredReservations) {
            try {
                // Double-check expiry-- avoid race conditions
                if (booking.getExpiresAt().isBefore(now) &&
                        booking.getStatus() == BookingStatus.RESERVED) {

                    booking.setStatus(BookingStatus.EXPIRED);
                    booking.setCancellationReason("Auto-expired at " + now);

                    rideReservationRepository.save(booking);
                    expiredCount++;

                    log.info("Expired reservation: {} for ride {} (expired at {})",
                            booking.getReservationUuid(),
                            booking.getRide().getUuid(),
                            booking.getExpiresAt());
                }
            } catch (Exception e) {
                failedCount++;
                log.error("Failed to expire reservation: {}", booking.getReservationUuid(), e);
            }
        }

        log.info("Expiry job completed: {} expired, {} failed", expiredCount, failedCount);
    }

    /**
     * Runs less frequently to clean up very old expired reservations
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    @Transactional
    public void cleanupOldExpiredReservations() {
        log.info("Running cleanup job for old expired reservations");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30); // 30 days old

        // delete or mark as archived
        int deletedCount = rideReservationRepository.bulkExpireReservations(cutoffDate);

        log.info("Cleanup job completed: {} old expired reservations processed", deletedCount);
    }
}
