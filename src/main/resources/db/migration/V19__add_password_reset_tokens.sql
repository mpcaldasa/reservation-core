CREATE TABLE password_reset_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'USED', 'REVOKED', 'EXPIRED')),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT password_reset_use_matches_status CHECK ((status = 'USED') = (used_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_password_reset_pending_user ON password_reset_tokens(user_id) WHERE status = 'PENDING';
CREATE INDEX ix_password_reset_expiration ON password_reset_tokens(expires_at) WHERE status = 'PENDING';
