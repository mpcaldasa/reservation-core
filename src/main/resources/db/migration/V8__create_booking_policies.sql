CREATE TABLE booking_policies (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), company_id uuid NOT NULL REFERENCES companies(id), name text NOT NULL,
 min_duration_minutes integer NOT NULL CHECK(min_duration_minutes>0), max_duration_minutes integer NOT NULL CHECK(max_duration_minutes>=min_duration_minutes),
 slot_increment_minutes integer NOT NULL CHECK(slot_increment_minutes>0), min_notice_minutes integer NOT NULL DEFAULT 0 CHECK(min_notice_minutes>=0),
 max_advance_days integer NOT NULL CHECK(max_advance_days>=0), cancellation_notice_minutes integer NOT NULL DEFAULT 0 CHECK(cancellation_notice_minutes>=0),
 approval_required boolean NOT NULL DEFAULT false, allow_customer_cancel boolean NOT NULL DEFAULT true, status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz);
CREATE UNIQUE INDEX uq_booking_policies_company_name_active ON booking_policies(company_id,lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX ix_booking_policies_company_active ON booking_policies(company_id) WHERE deleted_at IS NULL AND status='ACTIVE';
CREATE TABLE resource_policies (resource_id uuid NOT NULL REFERENCES resources(id), policy_id uuid NOT NULL REFERENCES booking_policies(id), effective_from timestamptz NOT NULL DEFAULT now(), effective_to timestamptz, PRIMARY KEY(resource_id,effective_from), CHECK(effective_to IS NULL OR effective_to>effective_from));
CREATE INDEX ix_resource_policies_resource_effective ON resource_policies(resource_id,effective_from DESC);
