CREATE TABLE availability_rules (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id uuid NOT NULL REFERENCES companies(id),
  resource_id uuid NOT NULL REFERENCES resources(id),
  weekday smallint NOT NULL CHECK (weekday BETWEEN 0 AND 6),
  start_local_time time NOT NULL,
  end_local_time time NOT NULL,
  effective_from date,
  effective_to date,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  CHECK (end_local_time > start_local_time),
  CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_availability_rules_company_resource_weekday_active
  ON availability_rules (company_id, resource_id, weekday)
  WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE TABLE resource_blocks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id uuid NOT NULL REFERENCES companies(id),
  resource_id uuid NOT NULL REFERENCES resources(id),
  start_at timestamptz NOT NULL,
  end_at timestamptz NOT NULL,
  reason text NOT NULL,
  block_type text NOT NULL CHECK (block_type IN ('MAINTENANCE', 'CLOSURE', 'ADMIN_BLOCK')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  CHECK (end_at > start_at)
);

CREATE INDEX ix_resource_blocks_company_resource_time_active
  ON resource_blocks (company_id, resource_id, start_at, end_at)
  WHERE deleted_at IS NULL;
