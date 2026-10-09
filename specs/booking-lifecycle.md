# Booking attendance lifecycle

Status: implemented for staff check-in and completion. Existing booking creation, approval, rejection, and cancellation remain available.

## API

| Operation | Endpoint | Authorized company roles |
| --- | --- | --- |
| Check in | `POST /api/v1/bookings/{bookingId}/check-in` | `COMPANY_ADMIN`, `BOOKING_MANAGER` |
| Complete | `POST /api/v1/bookings/{bookingId}/complete` | `COMPANY_ADMIN`, `BOOKING_MANAGER` |
| Mark no-show | `POST /api/v1/bookings/{bookingId}/no-show` | `COMPANY_ADMIN`, `BOOKING_MANAGER` |

Neither endpoint requires a request body. Each successful operation returns HTTP 200 and the updated booking response. The response adds `checkedInAt`, `checkedInBy`, `completedAt`, and `completedBy`; values are null before the relevant operation.

## State transitions

| Current state | Operation | Result |
| --- | --- | --- |
| `CONFIRMED` | Check in during the reserved interval | `CHECKED_IN` |
| `CHECKED_IN` | Check in again | Return current state without changes |
| `CHECKED_IN` | Complete at or after the recorded check-in time | `COMPLETED` |
| `COMPLETED` | Complete again | Return current state without changes |
| Any other combination | Check in or complete | HTTP 422 |

Check-in includes the start instant and excludes the end instant: `startAt <= now < endAt`. The application clock supplies the operation time; clients cannot override it. Operation timestamps use PostgreSQL microsecond precision so first responses and retries return the same persisted timestamp. Completion may occur before the scheduled end or after it, but never before the recorded check-in. Completion explicitly acknowledges that resource use has ended.

Repeated requests are idempotent while the booking is still in the operation's resulting state. They preserve the first actor and timestamp, even when the clock has moved outside the original check-in interval. A check-in repeated after completion returns HTTP 422. Retries do not append another audit entry or outbox event.

## Requirements

| ID | Requirement |
| --- | --- |
| BKL-001 | Company identity comes from the authenticated principal. Active membership and a staff role are required at the application boundary. Customers receive HTTP 403. Suspended memberships invalidate their company token and receive HTTP 401. |
| BKL-002 | Bookings are loaded and locked by both booking identifier and company identifier. A nonexistent booking or a booking owned by another company returns HTTP 404. |
| BKL-003 | Check-in is permitted only from `CONFIRMED` and within the reserved interval. Completion requires `CHECKED_IN` and a recorded check-in time. |
| BKL-004 | Each applied transition records its actor and timestamp, updates the booking and resource allocation together, appends an audit entry, and enqueues a notification event in one transaction. |
| BKL-005 | A missing allocation or an allocation with an inconsistent previous state returns HTTP 409 and rolls back the entire transition. |
| BKL-006 | Check-in preserves occupied capacity. Completion releases capacity, including any unused portion of the original interval. The scheduled booking start and end remain unchanged. |
| BKL-007 | Repeating an already applied operation preserves metadata and produces no additional side effects. |
| BKL-008 | Check-in, completion, cancellation, and approval/rejection serialize mutations using the same pessimistic booking row lock. |
| BKL-009 | Actor and timestamp fields must appear in pairs. Recorded check-in must lie inside the booking interval, completion must follow check-in, and attendance metadata must match the resulting state. These invariants are enforced by PostgreSQL constraints. |
| BKL-010 | Check-in and completion produce `BOOKING_CHECKED_IN` and `BOOKING_COMPLETED` events, supported by the English email template. |

## Architecture and migration

`BookingLifecycleController` exposes the staff endpoints. `BookingLifecycleService` verifies company access, locks the booking, and coordinates persistence and side effects. `Booking` owns the transition guards. `TimeConfiguration` provides a UTC `Clock`, which tests replace to verify boundary behavior without waiting.

Migration `V13__record_booking_attendance.sql` adds attendance metadata and constraints. Existing rows retain null metadata; no historical attendance timestamp or actor is inferred. A legacy checked-in row without recorded check-in metadata must be repaired before it can be completed through this API.

## Rescheduling

`PUT /api/v1/bookings/{bookingId}/reschedule` accepts `startAt` and `endAt`. Company administrators and booking managers may reschedule any pending or confirmed booking. Customers may reschedule only their own booking while its current policy permits customer cancellation and the cancellation notice window has not begun. Checked-in, completed, rejected, cancelled, and expired bookings cannot be rescheduled.

The new interval must have a positive whole-minute duration no longer than 24 hours, fit an active policy, satisfy its duration and increment rules, and be available under the resource schedule, blocks, notice, advance, and capacity rules. Availability excludes the booking being moved. Rescheduling preserves its current pending or confirmed status and customer, updates the booking and allocation atomically, and records one `BOOKING_RESCHEDULED` audit and outbox event. The company row is locked before the booking row, matching booking creation and serializing capacity checks. A request that selects the existing interval returns the booking without side effects.

## Pending expiration

`PendingBookingExpirationWorker` expires pending bookings after `app.booking.pending-timeout` (default `PT30M`). It runs in batches of 100, ordered by creation time, and uses PostgreSQL `SKIP LOCKED` so multiple application instances can claim independent rows. The booking and allocation move to `EXPIRED` together; each expiration records a system audit event with no user actor and enqueues `BOOKING_EXPIRED`. The partial index in `V15__index_pending_booking_expiration.sql` supports the due-booking scan. Polling defaults to 60 seconds after a 60 second initial delay and is configurable through `app.booking.expiration.poll-interval-ms` and `app.booking.expiration.initial-delay-ms`.

## No-show

Staff can mark a confirmed booking as a no-show only at or after its scheduled end. The transition records `noShowAt` and `noShowBy`, updates its allocation to `NO_SHOW`, releases occupied capacity, records one audit event, and enqueues `BOOKING_NO_SHOW`. Repeating the operation returns the existing result without another event. Pending bookings use the pending expiration policy instead. The V16 migration constrains the status, actor and timestamp, and ensures no-show metadata is consistent with the booking interval.

## Idempotency key expiration

Booking creation keys remain valid for `app.booking.idempotency.ttl` (default `PT24H`). A repeated request within that period returns the original booking when its request hash matches and returns HTTP 422 when the key is reused with a different request. Once expired, the key can be reused for a new booking. The create path removes an expired matching key while holding the company lock; a scheduled worker also deletes expired keys in batches of 500 using `SKIP LOCKED`. Its polling defaults to 60 seconds after a 60 second initial delay and can be configured with `app.booking.idempotency.cleanup-interval-ms` and `app.booking.idempotency.cleanup-initial-delay-ms`.

Automatic completion of unattended bookings, customer self check-in, and early-arrival grace periods remain outside this implemented scope.

## Acceptance coverage

| Scenario | Test |
| --- | --- |
| Domain interval boundaries, valid states, monotonic time, and idempotent metadata | `BookingLifecycleTest` |
| Staff workflow, capacity release, metadata, and exactly one event per applied transition | `BookingLifecycleApiIntegrationTest.staffCanCheckInAndCompleteWithoutDuplicatingEffects` |
| Customer, unauthenticated, and other-company access | `BookingLifecycleApiIntegrationTest.customersAndOtherCompaniesCannotManageAttendance` |
| Revoked membership with an existing token | `BookingLifecycleApiIntegrationTest.suspendedStaffCannotUseAnExistingToken` |
| Application clock and check-in interval | `BookingLifecycleApiIntegrationTest.checkInUsesTheBookingTimeWindow` |
| Pending bookings reject attendance operations | `BookingLifecycleApiIntegrationTest.pendingBookingsCannotCheckInOrComplete` |
| Check-in is required before completion and prevents cancellation | `BookingLifecycleApiIntegrationTest.completionRequiresCheckInAndCheckedInBookingsCannotBeCancelled` |
| Missing allocation rolls back all changes | `BookingLifecycleApiIntegrationTest.missingAllocationRollsBackTheAttendanceChange` |
| Concurrent retries append one audit and one outbox event | `BookingLifecycleApiIntegrationTest.concurrentCheckInsProduceOneAuditAndOneEvent` |
| Cancellation and check-in cannot both succeed | `BookingLifecycleApiIntegrationTest.checkInAndCancellationCannotBothSucceed` |
| PostgreSQL protects attendance metadata even when application guards are bypassed | `BookingLifecycleApiIntegrationTest.databaseProtectsAttendanceMetadata` |
| Timestamp precision remains stable across initial responses and retries | `BookingLifecycleApiIntegrationTest.attendanceTimesSurviveDatabaseRoundTrips` |
| English attendance notification content | `BookingEmailTemplateTest` |

Automated integration coverage for rescheduling, expiration cutoffs, worker contention, and notification staging remains pending. The expiration worker is designed for multi-instance execution through row-level skip-locked claims.
