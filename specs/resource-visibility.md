# Resource visibility

## Scope

Resource visibility controls the authenticated company catalog, availability, schedule rules, blocks, policy assignments, and new booking creation. `ResourceAccessService` is the shared application rule; HTTP controllers retain their existing role requirements.

## Access matrix

All access requires an active user, an active company, and an active membership in the company selected by the token.

| Visibility | Customer | Booking manager | Company administrator | Anonymous |
| --- | --- | --- | --- | --- |
| PUBLIC | Allowed | Allowed | Allowed | Denied |
| MEMBERS | Allowed | Allowed | Allowed | Denied |
| PRIVATE | Hidden | Allowed | Allowed | Denied |

`PUBLIC` does not currently expose an anonymous catalog. Any future public API needs its own explicit scope and response contract. Platform privileges do not substitute for company membership or company staff roles.

## Requirements

- **RV-001:** The company resource catalog omits private resources for members without `COMPANY_ADMIN` or `BOOKING_MANAGER`. Existing name ordering and resource status fields are preserved.
- **RV-002:** Resource-scoped reads return `404 Resource not found` for an inaccessible private resource, a deleted resource, or an unknown resource. A token for a different company cannot access the requested company.
- **RV-003:** Private resource creation of bookings requires a current staff role. Denied requests do not persist bookings, occupied capacity, idempotency keys, audit events, or notifications. Staff can book on behalf of an active company member.
- **RV-004:** Roles come from persisted company memberships. Removing a staff role affects subsequent requests made with an existing token. New booking visibility is checked after acquiring the company lock.
- **RV-005:** Existing booking ownership rules remain in effect. A customer can retrieve an existing booking created for them by staff, including one using a private resource. Booking history, cancellation, and an idempotent replay of an existing booking do not create a new reservation or grant resource catalog access.
- **RV-006:** Customers retain the ability to reserve public and member resources when the existing schedule, policy, and capacity checks succeed.

## Covered endpoints

- `GET /api/v1/companies/{companyId}/resources`
- `GET /api/v1/companies/{companyId}/resources/{resourceId}/availability`
- `GET /api/v1/companies/{companyId}/resources/{resourceId}/availability-rules`
- `GET /api/v1/companies/{companyId}/resources/{resourceId}/blocks`
- `GET /api/v1/companies/{companyId}/resources/{resourceId}/policies`
- `POST /api/v1/bookings` (company context comes from the authenticated principal)

Schedule and block creation also use the shared resource lookup. Administrative mutation endpoints retain their existing administrator-only controller checks.

## Verification

`ResourceVisibilityApiIntegrationTest` exercises the real HTTP API and PostgreSQL:

- Catalog filtering, both staff roles, and anonymous rejection: RV-001.
- Every resource-scoped read and cross-company isolation: RV-002.
- Denied writes with no side effects and staff booking for customers: RV-003.
- Demotion through the membership API followed by reuse of the old token: RV-004.
- Customer retrieval of staff-created private bookings: RV-005.
- Successful customer reservations for PUBLIC and MEMBERS: RV-006.

## Remaining work

Anonymous public browsing, resource editing and visibility changes, and catalog pagination remain outside this increment.
