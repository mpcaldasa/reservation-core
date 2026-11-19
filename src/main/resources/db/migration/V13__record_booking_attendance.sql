ALTER TABLE bookings
    ADD COLUMN checked_in_at timestamptz,
    ADD COLUMN checked_in_by uuid REFERENCES users(id),
    ADD COLUMN completed_at timestamptz,
    ADD COLUMN completed_by uuid REFERENCES users(id);

ALTER TABLE bookings
    ADD CONSTRAINT booking_check_in_actor_pair CHECK (
        (checked_in_at IS NULL) = (checked_in_by IS NULL)
    ),
    ADD CONSTRAINT booking_completion_actor_pair CHECK (
        (completed_at IS NULL) = (completed_by IS NULL)
    ),
    ADD CONSTRAINT booking_check_in_interval CHECK (
        checked_in_at IS NULL OR (checked_in_at >= start_at AND checked_in_at < end_at)
    ),
    ADD CONSTRAINT booking_attendance_order CHECK (
        completed_at IS NULL OR (checked_in_at IS NOT NULL AND completed_at >= checked_in_at)
    ),
    ADD CONSTRAINT booking_attendance_status CHECK (
        (checked_in_at IS NULL OR status IN ('CHECKED_IN', 'COMPLETED'))
        AND (completed_at IS NULL OR status = 'COMPLETED')
    );
