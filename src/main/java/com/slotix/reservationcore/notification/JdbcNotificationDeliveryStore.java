package com.slotix.reservationcore.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class JdbcNotificationDeliveryStore implements NotificationDeliveryStore {
    private final JdbcTemplate jdbc;

    public JdbcNotificationDeliveryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NotificationDelivery> claimNext(int maxAttempts, Duration leaseDuration) {
        jdbc.update("""
            WITH exhausted AS (
                SELECT id FROM notification_deliveries
                WHERE status = 'SENDING' AND claim_until <= now() AND attempts >= ?
                ORDER BY claim_until, id
                LIMIT 25 FOR UPDATE SKIP LOCKED
            )
            UPDATE notification_deliveries d
            SET status = 'FAILED', claim_token = NULL, claim_until = NULL,
                error_message = 'DELIVERY_LEASE_EXPIRED'
            FROM exhausted WHERE d.id = exhausted.id
            """, maxAttempts);

        return jdbc.query("""
            WITH candidate AS (
                SELECT id FROM notification_deliveries
                WHERE channel = 'EMAIL' AND attempts < ?
                  AND ((status IN ('PENDING', 'FAILED') AND next_attempt_at <= now())
                    OR (status = 'SENDING' AND claim_until <= now()))
                ORDER BY next_attempt_at, id
                LIMIT 1 FOR UPDATE SKIP LOCKED
            ), claimed AS (
                UPDATE notification_deliveries d
                SET status = 'SENDING', attempts = d.attempts + 1,
                    claim_token = gen_random_uuid(), claim_until = now() + (? * interval '1 second')
                FROM candidate WHERE d.id = candidate.id
                RETURNING d.*
            )
            SELECT d.id, d.claim_token, d.attempts, d.recipient, d.template,
                   b.booking_number, b.start_at, b.end_at, b.timezone, c.display_name
            FROM claimed d
            JOIN bookings b ON b.id = d.booking_id AND b.company_id = d.company_id
            JOIN companies c ON c.id = d.company_id
            """, JdbcNotificationDeliveryStore::mapDelivery, maxAttempts, leaseDuration.toSeconds())
            .stream().findFirst();
    }

    @Override
    public boolean markSent(NotificationDelivery delivery) {
        return jdbc.update("""
            UPDATE notification_deliveries
            SET status = 'SENT', sent_at = now(), claim_token = NULL, claim_until = NULL, error_message = NULL
            WHERE id = ? AND status = 'SENDING' AND claim_token = ?
            """, delivery.id(), delivery.claimToken()) == 1;
    }

    @Override
    public boolean markFailed(NotificationDelivery delivery, String errorCode, Duration retryDelay) {
        return jdbc.update("""
            UPDATE notification_deliveries
            SET status = 'FAILED', claim_token = NULL, claim_until = NULL, error_message = ?,
                next_attempt_at = now() + (? * interval '1 second')
            WHERE id = ? AND status = 'SENDING' AND claim_token = ?
            """, errorCode, retryDelay.toSeconds(), delivery.id(), delivery.claimToken()) == 1;
    }

    private static NotificationDelivery mapDelivery(ResultSet row, int index) throws SQLException {
        return new NotificationDelivery(row.getObject("id", UUID.class), row.getObject("claim_token", UUID.class),
            row.getInt("attempts"), row.getString("recipient"), row.getString("template"),
            row.getLong("booking_number"), row.getTimestamp("start_at").toInstant(),
            row.getTimestamp("end_at").toInstant(), row.getString("timezone"), row.getString("display_name"));
    }
}
