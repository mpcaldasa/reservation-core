package com.slotix.reservationcore.notification;

import com.slotix.reservationcore.observability.ScheduledJobObserver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingReminderWorker {
    private final JdbcTemplate jdbc;
    private final ScheduledJobObserver jobs;

    public BookingReminderWorker(JdbcTemplate jdbc, ScheduledJobObserver jobs) {
        this.jdbc = jdbc; this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.notification.reminder-delay-ms:30000}",
        initialDelayString = "${app.notification.reminder-initial-delay-ms:30000}")
    @Transactional
    public void enqueueDueReminders() {
        jobs.observe("booking-reminders", () -> jdbc.update("""
            WITH due AS (
                SELECT b.id,b.company_id,b.start_at,s.minutes_before
                FROM bookings b
                JOIN company_reminder_settings s ON s.company_id=b.company_id AND s.enabled
                JOIN companies c ON c.id=b.company_id AND c.status='ACTIVE' AND c.deleted_at IS NULL
                JOIN users u ON u.id=b.customer_user_id AND u.status='ACTIVE' AND u.deleted_at IS NULL
                JOIN company_memberships m ON m.company_id=b.company_id AND m.user_id=b.customer_user_id
                    AND m.status='ACTIVE' AND m.deleted_at IS NULL
                WHERE b.status='CONFIRMED' AND b.start_at>now()
                    AND b.start_at<=now() + s.minutes_before * interval '1 minute'
                    AND NOT EXISTS (SELECT 1 FROM outbox_events o WHERE o.company_id=b.company_id
                        AND o.aggregate_id=b.id AND o.event_type='BOOKING_REMINDER'
                        AND (o.payload->>'startAt')::timestamptz=b.start_at)
                ORDER BY b.start_at,b.id LIMIT 100 FOR UPDATE OF b SKIP LOCKED
            )
            INSERT INTO outbox_events(company_id,aggregate_type,aggregate_id,event_type,payload)
            SELECT company_id,'BOOKING',id,'BOOKING_REMINDER',
                jsonb_build_object('bookingId',id,'startAt',start_at,'minutesBefore',minutes_before)
            FROM due ON CONFLICT DO NOTHING
            """));
    }
}
