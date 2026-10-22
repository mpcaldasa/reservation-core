-- Reissue undelivered legacy links after deploying the encryption key.
UPDATE password_reset_tokens SET status = 'REVOKED'
WHERE status = 'PENDING' AND id IN (
    SELECT aggregate_id FROM identity_email_deliveries
    WHERE aggregate_type = 'PASSWORD_RESET' AND token IS NOT NULL
);
UPDATE company_invitations SET status = 'REVOKED'
WHERE status = 'PENDING' AND id IN (
    SELECT aggregate_id FROM identity_email_deliveries
    WHERE aggregate_type = 'COMPANY_INVITATION' AND token IS NOT NULL
);
UPDATE identity_email_deliveries
SET token = NULL, status = 'FAILED', claim_token = NULL, claim_until = NULL,
    error_code = 'LEGACY_TOKEN_REVOKED'
WHERE token IS NOT NULL;
ALTER TABLE identity_email_deliveries ADD CONSTRAINT identity_delivery_token_encrypted
    CHECK (token IS NULL OR token LIKE 'v1:%');
