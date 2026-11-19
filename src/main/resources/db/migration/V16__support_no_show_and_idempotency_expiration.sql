ALTER TABLE bookings
    DROP CONSTRAINT bookings_status_check,
    ADD CONSTRAINT bookings_status_check CHECK (
        status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'CHECKED_IN', 'COMPLETED', 'EXPIRED', 'NO_SHOW')
    ),
    ADD COLUMN no_show_at timestamptz,
    ADD COLUMN no_show_by uuid REFERENCES users(id),
    DROP CONSTRAINT booking_attendance_status,
    ADD CONSTRAINT booking_attendance_status CHECK (
        (checked_in_at IS NULL OR status IN ('CHECKED_IN', 'COMPLETED'))
        AND (completed_at IS NULL OR status = 'COMPLETED')
        AND ((no_show_at IS NULL) = (no_show_by IS NULL))
        AND ((status = 'NO_SHOW') = (no_show_at IS NOT NULL))
        AND (no_show_at IS NULL OR (status = 'NO_SHOW' AND no_show_at >= end_at
            AND checked_in_at IS NULL AND completed_at IS NULL))
    );

ALTER TABLE booking_resources
    DROP CONSTRAINT booking_resources_status_check,
    ADD CONSTRAINT booking_resources_status_check CHECK (
        status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED', 'NO_SHOW')
    );

CREATE INDEX ix_idempotency_keys_expiration ON idempotency_keys (expires_at, id);
