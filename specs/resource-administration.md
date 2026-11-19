# Resource administration

## Scope

Company administrators can update resource details, activate draft or inactive resources, and deactivate active resources. All mutations lock the active company before the resource and require the current persisted `COMPANY_ADMIN` role.

## Endpoints

| Operation | Endpoint | Result |
| --- | --- | --- |
| Update details | `PUT /api/v1/companies/{companyId}/resources/{resourceId}` | HTTP 200 with the updated resource |
| Activate | `POST /api/v1/companies/{companyId}/resources/{resourceId}/activate` | HTTP 200 with the active resource |
| Deactivate | `POST /api/v1/companies/{companyId}/resources/{resourceId}/deactivate` | HTTP 200 with the inactive resource |
| Delete | `DELETE /api/v1/companies/{companyId}/resources/{resourceId}` | HTTP 204; soft deletes the resource |
| Update weekly schedule | `PUT /api/v1/companies/{companyId}/resources/{resourceId}/availability-rules/{ruleId}` | HTTP 200 with the updated rule |
| Remove weekly schedule | `DELETE /api/v1/companies/{companyId}/resources/{resourceId}/availability-rules/{ruleId}` | HTTP 204; soft deletes the rule |
| Update block | `PUT /api/v1/companies/{companyId}/resources/{resourceId}/blocks/{blockId}` | HTTP 200 with the updated block |
| Remove block | `DELETE /api/v1/companies/{companyId}/resources/{resourceId}/blocks/{blockId}` | HTTP 204; soft deletes the block |

The update request contains `name`, `description`, `resourceType`, `capacity`, and `visibility`, with the same validation constraints as resource creation. The update preserves status and creation time. A capacity change returns HTTP 409 while a pending, confirmed, or checked-in reservation has an end time in the future. Other detail changes do not alter existing bookings. Duplicate names continue to return a conflict through the database uniqueness constraint.

Activation and deactivation are repeatable: a request that leaves the resource in its current state returns the current representation without changing timestamps or appending another audit event. Deactivation prevents new bookings and makes availability empty while preserving existing booking records. Activation permits booking again when the resource's policies, schedules, and capacity allow it.

Deletion is repeatable and returns HTTP 204 when the resource was already deleted. It returns HTTP 409 while a pending, confirmed, or checked-in reservation has an end time in the future. Successful deletion sets `deletedAt`, changes status to `INACTIVE`, and removes the resource from normal reads while retaining resource, schedule, policy assignment, and booking history. Deleted resources cannot be activated, updated, or used for new bookings.

## Audit and concurrency

Effective detail changes append `RESOURCE_UPDATED` with JSON snapshots of the resource's name, description, type, capacity, status, visibility, and deletion timestamp. State transitions append `RESOURCE_ACTIVATED` or `RESOURCE_DEACTIVATED`; deletion appends `RESOURCE_DELETED`. Repeated no-op requests produce no audit entry. Company row locking serializes resource capacity changes and deletion with booking creation; a resource row lock serializes updates to the same resource.

Schedule and block changes require `COMPANY_ADMIN`, validate the same constraints as creation, preserve their original identifiers and creation times, and use `updatedAt` at microsecond precision. Deletes set `deletedAt`, leave historical rows intact, and remove the item from future schedule and availability reads. A second delete of the same item returns HTTP 204 without another audit entry. Schedule and block mutations lock the active company before the resource and item, so booking creation observes either the prior or committed schedule configuration.

## Acceptance coverage

`ResourceApiIntegrationTest` covers idempotent deletion, active-reservation protection, activation/deactivation and detail-update audit behavior, plus schedule and block updates, soft deletion, and repeated-delete idempotency.
