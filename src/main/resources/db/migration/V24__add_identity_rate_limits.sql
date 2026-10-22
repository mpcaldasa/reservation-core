CREATE TABLE identity_rate_limits (
    bucket varchar(64) PRIMARY KEY,
    window_start timestamptz NOT NULL,
    attempts integer NOT NULL CHECK (attempts > 0)
);
CREATE INDEX ix_identity_rate_limits_window ON identity_rate_limits(window_start);
