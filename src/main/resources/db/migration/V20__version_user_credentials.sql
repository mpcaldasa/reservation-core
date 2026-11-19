ALTER TABLE users ADD COLUMN credential_version integer NOT NULL DEFAULT 0 CHECK (credential_version >= 0);
