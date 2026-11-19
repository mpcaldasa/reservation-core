CREATE TABLE resources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id uuid NOT NULL REFERENCES companies(id),
  name text NOT NULL,
  description text,
  resource_type text NOT NULL CHECK (resource_type IN ('SPACE', 'PERSON', 'EQUIPMENT', 'SERVICE_RESOURCE', 'OTHER')),
  capacity integer NOT NULL DEFAULT 1 CHECK (capacity > 0),
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'MAINTENANCE')),
  visibility text NOT NULL DEFAULT 'MEMBERS' CHECK (visibility IN ('PUBLIC', 'MEMBERS', 'PRIVATE')),
  metadata jsonb NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE UNIQUE INDEX uq_resources_company_name_active
  ON resources (company_id, lower(name))
  WHERE deleted_at IS NULL;

CREATE INDEX ix_resources_company_status_active
  ON resources (company_id, status)
  WHERE deleted_at IS NULL;
