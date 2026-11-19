package com.slotix.reservationcore.identity;

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
public class JdbcIdentityEmailDeliveryStore implements IdentityEmailDeliveryStore {
    private final JdbcTemplate jdbc;

    public JdbcIdentityEmailDeliveryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<IdentityEmailDelivery> claimNext(int maxAttempts, Duration leaseDuration) {
        jdbc.update("""
            UPDATE identity_email_deliveries SET token = NULL, error_code = 'DELIVERY_ATTEMPTS_EXHAUSTED'
            WHERE status = 'FAILED' AND attempts >= ? AND token IS NOT NULL
            """, maxAttempts);
        jdbc.update("""
            UPDATE identity_email_deliveries delivery
            SET status = 'FAILED', token = NULL, claim_token = NULL, claim_until = NULL,
                error_code = 'DELIVERY_ATTEMPTS_EXHAUSTED'
            WHERE status = 'SENDING' AND claim_until <= now() AND attempts >= ?
            """, maxAttempts);
        jdbc.update("""
            UPDATE identity_email_deliveries delivery
            SET token = NULL, status = 'FAILED', claim_token = NULL, claim_until = NULL,
                error_code = 'IDENTITY_TOKEN_UNAVAILABLE'
            WHERE delivery.token IS NOT NULL AND (
                (delivery.aggregate_type = 'COMPANY_INVITATION' AND NOT EXISTS (
                    SELECT 1 FROM company_invitations invitation WHERE invitation.id = delivery.aggregate_id
                      AND invitation.status = 'PENDING' AND invitation.expires_at > now()))
                OR (delivery.aggregate_type = 'PASSWORD_RESET' AND NOT EXISTS (
                    SELECT 1 FROM password_reset_tokens reset_token WHERE reset_token.id = delivery.aggregate_id
                      AND reset_token.status = 'PENDING' AND reset_token.expires_at > now()))
            )
            """);
        jdbc.update("""
            UPDATE company_invitations SET status = 'EXPIRED'
            WHERE status = 'PENDING' AND expires_at <= now()
            """);
        jdbc.update("""
            UPDATE password_reset_tokens SET status = 'EXPIRED'
            WHERE status = 'PENDING' AND expires_at <= now()
            """);

        return jdbc.query("""
            WITH candidate AS (
                SELECT delivery.id FROM identity_email_deliveries delivery
                WHERE delivery.attempts < ? AND delivery.token IS NOT NULL
                  AND ((delivery.status IN ('PENDING', 'FAILED') AND delivery.next_attempt_at <= now())
                    OR (delivery.status = 'SENDING' AND delivery.claim_until <= now()))
                  AND ((delivery.aggregate_type = 'COMPANY_INVITATION' AND EXISTS (
                        SELECT 1 FROM company_invitations invitation WHERE invitation.id = delivery.aggregate_id
                          AND invitation.status = 'PENDING' AND invitation.expires_at > now()))
                    OR (delivery.aggregate_type = 'PASSWORD_RESET' AND EXISTS (
                        SELECT 1 FROM password_reset_tokens reset_token WHERE reset_token.id = delivery.aggregate_id
                          AND reset_token.status = 'PENDING' AND reset_token.expires_at > now()))
                  )
                ORDER BY delivery.next_attempt_at, delivery.id
                LIMIT 1 FOR UPDATE SKIP LOCKED
            ), claimed AS (
                UPDATE identity_email_deliveries delivery
                SET status = 'SENDING', attempts = delivery.attempts + 1,
                    claim_token = gen_random_uuid(), claim_until = now() + (? * interval '1 second')
                FROM candidate WHERE delivery.id = candidate.id
                RETURNING delivery.*
            )
            SELECT delivery.id, delivery.claim_token, delivery.attempts, delivery.recipient, delivery.token,
                   delivery.aggregate_type,
                   (SELECT company.display_name FROM company_invitations invitation
                       JOIN companies company ON company.id = invitation.company_id
                    WHERE invitation.id = delivery.aggregate_id AND delivery.aggregate_type = 'COMPANY_INVITATION')
                       AS company_name
            FROM claimed delivery
            """, JdbcIdentityEmailDeliveryStore::mapDelivery, maxAttempts, leaseDuration.toSeconds())
            .stream().findFirst();
    }

    @Override
    public boolean markSent(IdentityEmailDelivery delivery) {
        return jdbc.update("""
            UPDATE identity_email_deliveries
            SET status = 'SENT', token = NULL, claim_token = NULL, claim_until = NULL, error_code = NULL
            WHERE id = ? AND status = 'SENDING' AND claim_token = ?
            """, delivery.id(), delivery.claimToken()) == 1;
    }

    @Override
    public boolean markFailed(IdentityEmailDelivery delivery, String errorCode, Duration retryDelay) {
        return jdbc.update("""
            UPDATE identity_email_deliveries
            SET status = 'FAILED', claim_token = NULL, claim_until = NULL, error_code = ?,
                next_attempt_at = now() + (? * interval '1 second')
            WHERE id = ? AND status = 'SENDING' AND claim_token = ?
            """, errorCode, retryDelay.toSeconds(), delivery.id(), delivery.claimToken()) == 1;
    }

    private static IdentityEmailDelivery mapDelivery(ResultSet row, int index) throws SQLException {
        return new IdentityEmailDelivery(row.getObject("id", UUID.class), row.getObject("claim_token", UUID.class),
            row.getInt("attempts"), row.getString("recipient"), row.getString("token"),
            row.getString("aggregate_type"), row.getString("company_name"));
    }
}
