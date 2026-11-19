CREATE TABLE revoked_access_tokens (
    token_id uuid PRIMARY KEY,
    expires_at timestamptz NOT NULL
);
CREATE INDEX ix_revoked_access_tokens_expiration ON revoked_access_tokens(expires_at);
