CREATE TABLE company_memberships (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES companies(id),
  user_id uuid NOT NULL REFERENCES users(id),
  status text NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED')),
  joined_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE UNIQUE INDEX uq_company_memberships_active
  ON company_memberships (tenant_id, user_id)
  WHERE deleted_at IS NULL;

CREATE INDEX ix_company_memberships_user_active
  ON company_memberships (user_id)
  WHERE deleted_at IS NULL;

CREATE TABLE membership_roles (
  membership_id uuid NOT NULL REFERENCES company_memberships(id),
  role text NOT NULL CHECK (role IN ('COMPANY_ADMIN', 'BOOKING_MANAGER', 'CUSTOMER')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (membership_id, role)
);

CREATE TABLE platform_user_roles (
  user_id uuid NOT NULL REFERENCES users(id),
  role text NOT NULL CHECK (role IN ('PLATFORM_ADMIN')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role)
);

CREATE UNIQUE INDEX uq_users_email_active
  ON users (email)
  WHERE deleted_at IS NULL;

INSERT INTO company_memberships (
  id, tenant_id, user_id, status, joined_at, created_at, updated_at, deleted_at
)
SELECT
  gen_random_uuid(),
  company_id,
  id,
  CASE WHEN status = 'ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END,
  CASE WHEN status = 'ACTIVE' THEN created_at ELSE NULL END,
  created_at,
  updated_at,
  deleted_at
FROM users
WHERE deleted_at IS NULL;

INSERT INTO membership_roles (membership_id, role, created_at)
SELECT
  membership.id,
  CASE
    WHEN legacy_user.role IN ('OWNER', 'ADMIN') THEN 'COMPANY_ADMIN'
    WHEN legacy_user.role = 'STAFF' THEN 'BOOKING_MANAGER'
  END,
  legacy_user.created_at
FROM users legacy_user
JOIN company_memberships membership
  ON membership.user_id = legacy_user.id
 AND membership.tenant_id = legacy_user.company_id
WHERE legacy_user.deleted_at IS NULL;
