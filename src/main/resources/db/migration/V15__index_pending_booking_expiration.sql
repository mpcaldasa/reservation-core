CREATE INDEX ix_bookings_pending_expiration
    ON bookings (created_at, id)
    WHERE status = 'PENDING';
