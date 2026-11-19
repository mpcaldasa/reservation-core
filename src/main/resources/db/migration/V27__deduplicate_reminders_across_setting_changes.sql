DROP INDEX uq_booking_reminder_occurrence;
CREATE UNIQUE INDEX uq_booking_reminder_occurrence
    ON outbox_events(company_id, aggregate_id, (payload->>'startAt'))
    WHERE event_type = 'BOOKING_REMINDER';
CREATE INDEX ix_bookings_confirmed_reminders ON bookings(company_id, start_at, id)
    WHERE status = 'CONFIRMED';
