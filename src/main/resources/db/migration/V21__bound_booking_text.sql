-- NOT VALID preserves historical rows; new writes enforce the limits immediately.
ALTER TABLE bookings ADD CONSTRAINT booking_notes_length CHECK (char_length(notes) <= 2000) NOT VALID;
ALTER TABLE bookings ADD CONSTRAINT booking_cancellation_reason_length CHECK (char_length(cancellation_reason) <= 2000) NOT VALID;
ALTER TABLE resource_blocks ADD CONSTRAINT resource_block_reason_length CHECK (char_length(reason) <= 2000) NOT VALID;
