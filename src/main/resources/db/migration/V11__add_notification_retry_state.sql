ALTER TABLE notification_deliveries
    ADD COLUMN attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN claim_until timestamptz;

ALTER TABLE notification_deliveries DROP CONSTRAINT notification_deliveries_status_check;
ALTER TABLE notification_deliveries ADD CONSTRAINT notification_deliveries_status_check
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'));

CREATE INDEX ix_notification_due ON notification_deliveries(next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED', 'SENDING');
