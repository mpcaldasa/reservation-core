CREATE TABLE company_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id),
    invited_email citext NOT NULL,
    invited_by uuid NOT NULL REFERENCES users(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT company_invitation_acceptance_matches_status CHECK
        ((status = 'ACCEPTED') = (accepted_at IS NOT NULL))
);

CREATE TABLE company_invitation_roles (
    invitation_id uuid NOT NULL REFERENCES company_invitations(id) ON DELETE CASCADE,
    role text NOT NULL CHECK (role IN ('COMPANY_ADMIN', 'CUSTOMER')),
    PRIMARY KEY (invitation_id, role)
);

CREATE UNIQUE INDEX uq_company_invitation_pending_email
    ON company_invitations(company_id, invited_email) WHERE status = 'PENDING';
CREATE INDEX ix_company_invitation_expiration
    ON company_invitations(expires_at) WHERE status = 'PENDING';

CREATE TABLE company_invitation_deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id uuid NOT NULL UNIQUE REFERENCES company_invitations(id) ON DELETE CASCADE,
    recipient citext NOT NULL,
    token text,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    claim_token uuid,
    claim_until timestamptz,
    error_code text,
    CONSTRAINT invitation_delivery_claim_matches_status CHECK (
        (status = 'SENDING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL)
        OR (status <> 'SENDING' AND claim_token IS NULL AND claim_until IS NULL)
    ),
    CONSTRAINT invitation_delivery_token_available CHECK (status <> 'PENDING' OR token IS NOT NULL)
);

CREATE INDEX ix_company_invitation_delivery_due
    ON company_invitation_deliveries(next_attempt_at, id) WHERE status IN ('PENDING', 'FAILED', 'SENDING');
