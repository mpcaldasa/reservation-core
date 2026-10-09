package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.observability.ScheduledJobObserver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Service
public class IdempotencyKeyCleanupWorker {
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ScheduledJobObserver jobs;

    public IdempotencyKeyCleanupWorker(JdbcTemplate jdbc, Clock clock, ScheduledJobObserver jobs) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.booking.idempotency.cleanup-interval-ms:60000}",
        initialDelayString = "${app.booking.idempotency.cleanup-initial-delay-ms:60000}")
    @Transactional
    public int deleteExpiredKeys() {
        return jobs.observe("idempotency-key-cleanup", this::deleteBatch);
    }

    private int deleteBatch() {
        return jdbc.update("""
            DELETE FROM idempotency_keys
            WHERE id IN (
                SELECT id FROM idempotency_keys
                WHERE expires_at <= ?
                ORDER BY expires_at, id
                LIMIT %d
                FOR UPDATE SKIP LOCKED
            )
            """.formatted(BATCH_SIZE), clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
