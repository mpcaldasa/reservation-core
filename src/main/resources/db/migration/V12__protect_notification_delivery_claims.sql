ALTER TABLE notification_deliveries ADD COLUMN claim_token uuid;

UPDATE notification_deliveries SET claim_token = gen_random_uuid() WHERE status = 'SENDING';

ALTER TABLE notification_deliveries
    ADD CONSTRAINT notification_attempts_nonnegative CHECK (attempts >= 0),
    ADD CONSTRAINT notification_claim_matches_status CHECK (
        (status = 'SENDING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL)
        OR (status <> 'SENDING' AND claim_token IS NULL AND claim_until IS NULL)
    );

CREATE INDEX ix_notification_expired_claims ON notification_deliveries(claim_until, id)
    WHERE status = 'SENDING';
