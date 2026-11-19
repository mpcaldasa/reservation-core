CREATE TABLE company_reminder_settings (
    company_id uuid PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    enabled boolean NOT NULL DEFAULT false,
    minutes_before integer NOT NULL CHECK (minutes_before BETWEEN 1 AND 10080),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_booking_reminder_occurrence
    ON outbox_events(company_id, aggregate_id, (payload->>'startAt'), (payload->>'minutesBefore'))
    WHERE event_type = 'BOOKING_REMINDER';
ALTER TABLE notification_deliveries DROP CONSTRAINT notification_deliveries_status_check;
ALTER TABLE notification_deliveries ADD CONSTRAINT notification_deliveries_status_check
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED'));
