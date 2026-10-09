package com.slotix.reservationcore.notification;

import com.slotix.reservationcore.observability.ScheduledJobObserver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationQueueWorker {
    private final JdbcTemplate jdbc;
    private final ScheduledJobObserver jobs;

    public NotificationQueueWorker(JdbcTemplate jdbc, ScheduledJobObserver jobs) {
        this.jdbc = jdbc;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.notification.queue-delay-ms:5000}",
        initialDelayString = "${app.notification.queue-initial-delay-ms:5000}")
    @Transactional
    public void stagePendingDeliveries() {
        jobs.observe("notification-outbox", this::stageBatch);
    }

    private void stageBatch() {
        List<UUID> eventIds = jdbc.query("""
            SELECT id FROM outbox_events
            WHERE processed_at IS NULL AND aggregate_type = 'BOOKING'
            ORDER BY occurred_at, id
            LIMIT 25 FOR UPDATE SKIP LOCKED
            """, (row, index) -> row.getObject(1, UUID.class));

        for (UUID eventId : eventIds) {
            stageDelivery(eventId);
        }
    }

    private void stageDelivery(UUID eventId) {
        jdbc.update("""
            INSERT INTO notification_deliveries
                (company_id, booking_id, outbox_event_id, channel, template, recipient, status)
            SELECT o.company_id, b.id, o.id, 'EMAIL', o.event_type, u.email, 'PENDING'
            FROM outbox_events o
            JOIN bookings b ON b.id = o.aggregate_id AND b.company_id = o.company_id
            JOIN users u ON u.id = b.customer_user_id
            WHERE o.id = ?
            ON CONFLICT (outbox_event_id) DO NOTHING
            """, eventId);

        int processed = jdbc.update("""
            UPDATE outbox_events o
            SET processed_at = now(), attempts = attempts + 1
            WHERE o.id = ? AND EXISTS (
                SELECT 1 FROM notification_deliveries d WHERE d.outbox_event_id = o.id
            )
            """, eventId);
        if (processed != 1) {
            throw new IllegalStateException("Booking outbox event could not be staged: " + eventId);
        }
    }
}
