CREATE TABLE google_calendar_connections (
    company_id uuid PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    connected_by uuid NOT NULL REFERENCES users(id),
    calendar_id varchar(1024) NOT NULL,
    refresh_token_ciphertext text NOT NULL,
    connected_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE google_calendar_oauth_states (
    state_hash varchar(64) PRIMARY KEY,
    company_id uuid NOT NULL UNIQUE REFERENCES companies(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    verifier_ciphertext text NOT NULL,
    expires_at timestamptz NOT NULL
);
