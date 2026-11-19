CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id),
    booking_number bigint GENERATED ALWAYS AS IDENTITY,
    customer_user_id uuid NOT NULL REFERENCES users(id),
    resource_id uuid NOT NULL REFERENCES resources(id),
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'CHECKED_IN', 'COMPLETED', 'EXPIRED')),
    timezone text NOT NULL,
    notes text,
    cancellation_reason text,
    cancelled_at timestamptz,
    cancelled_by uuid REFERENCES users(id),
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (end_at > start_at)
);
CREATE INDEX ix_bookings_company_start ON bookings(company_id, start_at DESC);
CREATE INDEX ix_bookings_customer_start ON bookings(customer_user_id, start_at DESC);

CREATE TABLE booking_resources (
    booking_id uuid NOT NULL REFERENCES bookings(id),
    company_id uuid NOT NULL REFERENCES companies(id),
    resource_id uuid NOT NULL REFERENCES resources(id),
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    units integer NOT NULL DEFAULT 1 CHECK (units > 0),
    status text NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    single_capacity boolean NOT NULL,
    time_range tstzrange GENERATED ALWAYS AS (tstzrange(start_at, end_at, '[)')) STORED,
    PRIMARY KEY (booking_id, resource_id),
    CHECK (end_at > start_at)
);

CREATE FUNCTION verify_booking_resource() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE resource_company uuid;
DECLARE resource_capacity integer;
BEGIN
    SELECT company_id, capacity INTO resource_company, resource_capacity FROM resources WHERE id = NEW.resource_id;
    IF resource_company IS DISTINCT FROM NEW.company_id OR
       NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = NEW.booking_id AND b.company_id = NEW.company_id
           AND b.resource_id = NEW.resource_id AND b.start_at = NEW.start_at AND b.end_at = NEW.end_at
           AND b.status = NEW.status) THEN
        RAISE EXCEPTION 'Booking resource must match its booking and company';
    END IF;
    NEW.single_capacity := resource_capacity = 1;
    RETURN NEW;
END;
$$;
CREATE TRIGGER booking_resource_guard BEFORE INSERT OR UPDATE ON booking_resources
    FOR EACH ROW EXECUTE FUNCTION verify_booking_resource();

ALTER TABLE booking_resources ADD CONSTRAINT no_overlapping_single_capacity_booking
    EXCLUDE USING gist (resource_id WITH =, time_range WITH &&)
    WHERE (single_capacity AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN'));
CREATE INDEX ix_booking_resources_range ON booking_resources USING gist(resource_id, time_range);

CREATE TABLE idempotency_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id),
    idempotency_key text NOT NULL,
    request_hash text NOT NULL,
    booking_id uuid NOT NULL REFERENCES bookings(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    UNIQUE (company_id, idempotency_key)
);
