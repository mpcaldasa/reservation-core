CREATE TABLE identity_email_deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type text NOT NULL CHECK (aggregate_type IN ('COMPANY_INVITATION', 'PASSWORD_RESET')),
    aggregate_id uuid NOT NULL,
    recipient citext NOT NULL,
    token text,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    claim_token uuid,
    claim_until timestamptz,
    error_code text,
    CONSTRAINT identity_email_delivery_claim_matches_status CHECK (
        (status = 'SENDING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL)
        OR (status <> 'SENDING' AND claim_token IS NULL AND claim_until IS NULL)
    ),
    CONSTRAINT identity_email_delivery_token_available CHECK (status <> 'PENDING' OR token IS NOT NULL),
    CONSTRAINT uq_identity_email_delivery_aggregate UNIQUE (aggregate_type, aggregate_id)
);

INSERT INTO identity_email_deliveries (
    id, aggregate_type, aggregate_id, recipient, token, status, attempts, next_attempt_at,
    claim_token, claim_until, error_code
)
SELECT id, 'COMPANY_INVITATION', invitation_id, recipient, token, status, attempts, next_attempt_at,
       claim_token, claim_until, error_code
FROM company_invitation_deliveries;

DROP TABLE company_invitation_deliveries;

CREATE INDEX ix_identity_email_delivery_due
    ON identity_email_deliveries(next_attempt_at, id) WHERE status IN ('PENDING', 'FAILED', 'SENDING');
