CREATE TABLE audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES companies(id),
    actor_user_id uuid,
    action text NOT NULL,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    before_data jsonb,
    after_data jsonb,
    correlation_id text,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_company_entity ON audit_logs(company_id, entity_type, entity_id, created_at DESC);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_error text
);
CREATE INDEX ix_outbox_unprocessed ON outbox_events(occurred_at) WHERE processed_at IS NULL;

CREATE TABLE notification_deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id),
    booking_id uuid NOT NULL REFERENCES bookings(id),
    outbox_event_id uuid NOT NULL UNIQUE REFERENCES outbox_events(id),
    channel text NOT NULL CHECK (channel IN ('EMAIL')),
    template text NOT NULL,
    recipient text NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    provider_id text,
    sent_at timestamptz,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_pending ON notification_deliveries(created_at) WHERE status = 'PENDING';
