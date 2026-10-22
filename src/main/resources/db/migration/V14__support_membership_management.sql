ALTER TABLE company_memberships ADD COLUMN version integer NOT NULL DEFAULT 0;

CREATE INDEX ix_company_memberships_company_current
    ON company_memberships(company_id, created_at, id) WHERE deleted_at IS NULL;
