# Full-stack QA report — 2026-10-21

## Scope and environment

This report covers the real Slotix frontend at `http://127.0.0.1:4200` and the local backend at
`http://127.0.0.1:8080`. The normal local PostgreSQL database was used at the user's request so
the resulting QA data remains visible in the frontend.

The browser run created a dedicated company, users, resources, policies, assignments, schedules,
blocks, and reservations. All data uses the `browser-qamvin6yii` QA marker.

| Item | Value |
| --- | --- |
| QA company ID | `3b990fb0-3b30-48e3-b22a-75469ec99059` |
| QA customer ID | `fb9e0874-1742-4e19-973e-c9ad6ad96d8f` |
| Automatic resource ID | `e815de09-09ef-4f80-900f-3ef76cd91847` |
| Approval resource ID | `b5ae59d3-8e10-426b-b46f-5122833b4cac` |
| Customer booking completed through the UI | `276f2b93-c46e-4fe9-b126-f01004a218b8` |
| Customer booking cancelled through the UI | `ebbfba31-100a-4def-98e5-b385e680da08` |
| Approval-required booking approved through the UI | `634b2095-8ce2-4447-8510-0fa38d2ce10e` |

All passwords and JWTs are intentionally omitted from this report and its HTTP evidence.

## Browser test results

| Area | Cases exercised from the Angular UI | Result |
| --- | --- | --- |
| Platform access | Platform login and company onboarding with an initial company administrator | Pass |
| Company authentication | Invalid password rejection and valid administrator and customer sign-in | Pass |
| Memberships | Customer and booking-manager registration, suspension, and reactivation | Pass |
| Authorization | Customer access to `/members`, `/policies`, and platform operations is denied | Pass |
| Resources | Two resources created as drafts, activated, and configured with a weekly schedule | Pass |
| Policies | Automatic and approval-required policies created and assigned to the appropriate resource | Pass |
| Availability | Allowed 30-minute slots returned; a 45-minute duration returned no slots because it violates the policy increment | Pass |
| Blocks | A maintenance block was created through the resource detail UI | Pass |
| Customer bookings | One confirmed booking created and cancelled; a second confirmed booking displayed in the calendar | Pass |
| Booking rules | A future booking rejected early check-in with `422`; this is the expected time-window rule | Pass |
| Booking lifecycle | A QA fixture was moved into the active window, then checked in and completed from the UI | Pass |
| Approval flow | A pending booking was approved from the administrator UI | Pass |

The extended browser run completed **53 UI assertions** and recorded **95 HTTP exchanges**:

| HTTP status | Count | Meaning |
| --- | ---: | --- |
| `200` | 79 | Successful reads and state changes |
| `201` | 13 | Successful creation operations |
| `401` | 2 | Deliberate invalid-login checks |
| `422` | 1 | Deliberate early check-in check |

## HTTP evidence

[Complete browser HTTP evidence with every generated curl command and observed response](2026-10-21-browser-http-evidence.md)
contains the 95 exchanges. It is generated from live browser traffic through Angular's `/api` proxy.
Each request body, response body, status, URL, and equivalent `curl` command is included. Passwords,
JWTs, hashes, and email addresses are redacted.

Representative observed requests follow.

```bash
curl -i -X POST 'http://localhost:8080/api/platform/login' \
  -H 'Content-Type: application/json' \
  -d '{"email":"slotix.qa.platform@example.test","password":"<redacted>"}'
```

```http
HTTP/1.1 200 OK
```

```json
{"token":"<redacted>","tokenType":"Bearer"}
```

```bash
curl -i -X GET \
  'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=30' \
  -H 'Authorization: Bearer <redacted>'
```

```http
HTTP/1.1 200 OK
```

```json
[
  {"startAt":"2026-10-22T13:00:00Z","endAt":"2026-10-22T13:30:00Z"}
]
```

```bash
curl -i -X POST 'http://localhost:8080/api/users/login' \
  -H 'Content-Type: application/json' \
  -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","password":"<redacted>"}'
```

```http
HTTP/1.1 401 Unauthorized
```

```json
{"status":401,"code":"INVALID_CREDENTIALS","error":"Invalid email or password","fields":{}}
```

## Automated suite results

| Command | Result |
| --- | --- |
| `./mvnw -q test` | Pass — 112 tests, 0 failures, 0 errors, 0 skipped; PostgreSQL Testcontainers was used |
| `npm run test -- --watch=false` | Pass — 10 files, 29 tests |
| `npm run lint` | Pass |
| `npm run typecheck` | Pass |
| `SLOTIX_QA_CREDENTIALS=/private/tmp/slotix-qa-credentials.json npm run e2e:real` | Pass — real onboarding, resource, policy, availability, reservation, and cancellation flow |

## Fixture note for attendance completion

The primary browser booking was created for the next company day, so early check-in correctly
returned `422`. To prove the positive lifecycle through the frontend without waiting for the
future time window, only that QA booking's `start_at` and `end_at` values were moved into the
active window in local PostgreSQL. The administrator then used the UI to check in and complete
the booking. The persisted final state is `COMPLETED`.

## Finding

**Open defect — hard refresh clears the authenticated frontend session.**

The session is held only in Angular memory. After a successful login, a full browser reload on a
protected route returns the user to `/login` even when the issued JWT is still valid. The normal
single-page navigation paths work correctly. This should be fixed by securely persisting the
session or restoring it from a secure server-managed session before the application evaluates
route guards.

## Limits explicitly not represented as passed

SMTP is disabled in this local environment. Password-reset and invitation delivery were not
claimed as end-to-end passes because no real email inbox was configured. The corresponding API and
notification behavior are covered by the backend test suite.
