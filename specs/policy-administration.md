# Booking policy administration

## Endpoints

Policy write endpoints require an active company and the current `COMPANY_ADMIN` role. The company row is locked before a write so policy changes serialize with booking creation and resource assignment. Resource assignment listing allows company administrators, booking managers, and customers with resource visibility.

| Operation | Endpoint | Result |
| --- | --- | --- |
| List policies | `GET /api/v1/companies/{companyId}/booking-policies` | HTTP 200, active and inactive policies |
| Create policy | `POST /api/v1/companies/{companyId}/booking-policies` | HTTP 201 with the new identifier |
| Update policy | `PUT /api/v1/companies/{companyId}/booking-policies/{policyId}` | HTTP 200 with the updated policy |
| Activate policy | `POST /api/v1/companies/{companyId}/booking-policies/{policyId}/activate` | HTTP 200 with the active policy |
| Deactivate policy | `POST /api/v1/companies/{companyId}/booking-policies/{policyId}/deactivate` | HTTP 200 with the inactive policy |
| List resource assignments | `GET /api/v1/companies/{companyId}/resources/{resourceId}/policies` | HTTP 200 with assignments ordered by start |
| Assign a policy | `POST /api/v1/companies/{companyId}/resources/{resourceId}/policies` | HTTP 201 with the assignment |
| Close an assignment | `PUT /api/v1/companies/{companyId}/resources/{resourceId}/policies/{effectiveFrom}` | HTTP 200 with the shortened assignment |
| Replace an assignment policy | `PUT /api/v1/companies/{companyId}/resources/{resourceId}/policies/{effectiveFrom}/policy` | HTTP 200 with the updated assignment |
| Remove a future assignment | `DELETE /api/v1/companies/{companyId}/resources/{resourceId}/policies/{effectiveFrom}` | HTTP 204 |

Create and update share the policy fields: name, minimum and maximum duration, slot increment, minimum notice, maximum advance, cancellation notice, approval requirement, and customer cancellation permission. Durations must be positive, the increment must be positive, and both duration bounds must be divisible by the increment. Names are limited to 120 characters; duplicate company names return HTTP 409.

## Safe lifecycle rules

- Updates are allowed only before a policy has ever been assigned. Once a `resource_policies` row references a policy, its terms remain immutable so previous bookings retain the original cancellation behavior. Create another policy for changed terms.
- Deactivation returns HTTP 409 while any resource assignment has no end or ends in the future. After assignments end, the policy can be deactivated. Its historical assignments remain, and existing booking cancellation can still read the inactive policy's terms.
- Only active policies can receive new resource assignments or produce new availability and bookings. Reactivating an inactive policy restores those behaviors for its existing assignments.
- Repeated activate or deactivate requests are no-ops: they return the current policy without changing timestamps or adding audit entries.
- Create, effective update, activation, and deactivation record a corresponding audit event in the same transaction.

- Assignment closure can only shorten its interval. It returns HTTP 409 if an active reservation would extend beyond the new end. Removing an assignment is allowed only when its start is in the future and it has no active reservations; past and current assignment history is retained. Assignment writes lock the company and resource rows so they serialize with booking creation and rescheduling.
- Replacing an assignment's policy requires an active policy in the same company and returns HTTP 409 while pending, confirmed, or checked-in reservations use that assignment. This protects existing bookings because their cancellation and rescheduling terms are resolved from the assignment rather than copied onto the booking. The interval and assignment identity remain unchanged; replacement is audited in the same transaction. Replacing with the already assigned active policy is a no-op.
- Assignment failures return stable codes such as `POLICY_ASSIGNMENT_OVERLAP`, `POLICY_ASSIGNMENT_CANNOT_BE_EXTENDED`, and `ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_CHANGE`; see the [API error contract](api-errors.md).
- Booking policy rows are not deleted because resource assignments and bookings need their historical terms.

## Verification status

Integration coverage verifies assignment overlap and interval errors, policy replacement, assignment closure and removal, active-reservation protection, and serialization of overlapping concurrent assignments. Policy lifecycle permissions and historical cancellation behavior still need automated coverage.
