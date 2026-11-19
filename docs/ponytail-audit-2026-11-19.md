# Slotix code audit

What this repo does: `reservation-core` is the Java/Spring modular backend for a multi-company reservation SaaS, using PostgreSQL for bookings, identity and job coordination. `slotix-web` is its Angular browser application. This review assumes concurrent customers and staff, with multiple backend instances sharing one database; no throughput target was supplied or measured.

The requested audit was followed by implementation and a review of those changes. Findings below describe the original behavior and the fixes made during this pass. Repository documents were used as evidence of intended behavior.

## Must fix

1. **Available capacity was understated** (`src/main/java/com/slotix/reservationcore/availability/AvailabilityService.java:89`, `booking/BookingService.java:132`).
   - **What this is:** Availability and booking creation check whether a resource has room for another booking.
   - **Problem:** Two adjacent half-hour bookings counted as two occupied units for a one-hour request, even though they never run together. Capacity two incorrectly rejected that request; rescheduling inherited the same error.
   - **Fix:** Calculate intervals where reserved units reach capacity, then test the candidate against those intervals. Remove the duplicate, incorrect count query from creation while retaining its transaction lock.
   - **If we skip it:** Customers cannot book valid slots and staff see misleading availability.
   - **Status:** Fixed. Creation and rescheduling integration regressions cover adjacent bookings and rejection once capacity is actually full.

2. **Edits could reserve an old search result** (`slotix-web/src/app/features/bookings/bookings.page.ts:430`).
   - **What this is:** The booking form saves the search values while showing available slots.
   - **Problem:** Changing the date, duration or resource left old slots usable. Notes changed after searching were also omitted from the request.
   - **Fix:** Clear slots when search fields change, update other request fields, and reject submission of slots outside the current results.
   - **If we skip it:** A customer can create a booking using values different from those currently displayed.
   - **Status:** Fixed. Component tests cover invalidation, blocked stale submission and edited notes.

3. **Forms remained editable while saving** (`slotix-web/src/app/shared/editor.ts:62`).
   - **What this is:** The shared editor handles administrative and booking forms.
   - **Problem:** Its buttons were disabled during a request, but the inputs remained editable. A successful response could mark unsent edits as saved.
   - **Fix:** Disable native controls while busy, keeping their form values intact.
   - **If we skip it:** The saved record can differ from what the user believes was submitted.
   - **Status:** Fixed. The existing busy-state test now verifies the input is disabled and submissions remain blocked.

4. **Overlapping rules produced duplicate slots** (`src/main/java/com/slotix/reservationcore/availability/AvailabilityService.java:91`).
   - **What this is:** Weekly availability rules can overlap.
   - **Problem:** Each rule appended its slots, so the same start appeared more than once and global ordering was not guaranteed. The browser uses start times as row identities.
   - **Fix:** Collect slots by start instant in an ordered map.
   - **If we skip it:** Customers see duplicate choices and the browser receives duplicate row identities.
   - **Status:** Fixed. A PostgreSQL-backed regression verifies unique chronological results.

5. **Impossible dates silently changed calendar ranges** (`slotix-web/src/app/core/time.ts:27`, `features/bookings/bookings.page.ts:34`).
   - **What this is:** Local calendar dates become API range instants.
   - **Problem:** JavaScript normalized February 31 to a March date. The calendar end-date offset could normalize invalid URL values before validation.
   - **Fix:** Validate the parsed date against the original string, and reuse that validation before adding a day.
   - **If we skip it:** The calendar requests dates different from the supplied range.
   - **Status:** Fixed. Tests cover impossible dates, a valid leap day and rejection before a calendar API request.

6. **The API accepted almost 32-day ranges** (`src/main/java/com/slotix/reservationcore/booking/BookingService.java:165`).
   - **What this is:** Calendar requests are limited to 31 days.
   - **Problem:** Conversion to whole days discarded fractions, accepting 31 days plus additional hours.
   - **Fix:** Compare the complete duration against 31 days.
   - **If we skip it:** The documented limit is bypassed and oversized reads are accepted.
   - **Status:** Fixed. Exactly 31 days succeeds; 31 days plus one nanosecond fails.

## Should fix

7. **Customer calendars loaded other customers' rows** (`src/main/java/com/slotix/reservationcore/booking/BookingRepository.java:28`).
   - **What this is:** Calendar retrieval previously fetched every company booking in the range before filtering by customer in Java.
   - **Problem:** A customer with one booking could cause all company bookings in that range to be materialized. The existing response filter prevented disclosure; this was unnecessary database transfer and heap use, not a confirmed data leak.
   - **Fix:** Include customer ownership in the query while preserving authorized staff visibility and deterministic ordering.
   - **If we skip it:** Customer requests become more expensive as company booking volume grows.
   - **Status:** Fixed. An integration test verifies customer scoping and staff visibility.

8. **Availability ignored the configured clock** (`src/main/java/com/slotix/reservationcore/availability/AvailabilityService.java:77`).
   - **What this is:** Notice periods and advance limits depend on the current instant.
   - **Problem:** Availability read the system clock while booking lifecycle operations used the application clock. Tests or deployments supplying a clock could get inconsistent time decisions.
   - **Fix:** Inject the existing application `Clock` and read it once per calculation.
   - **If we skip it:** Time-dependent behavior cannot be verified consistently with the rest of booking logic.
   - **Status:** Fixed. The controlled-clock integration test verifies that past slots disappear.

The frontend architecture document also incorrectly stated that logout/revocation did not exist. It now describes the existing endpoint and shared database revocations. No dependencies or architectural layers were added.

## Verification and limits

- Before the fixes, three focused backend regressions failed and four frontend regressions failed. These runs established the capacity, duplicate-slot, range-limit, stale-request, busy-input and date-parser defects.
- Backend checks use isolated PostgreSQL Testcontainers, not the development database. The final commands and results are recorded in the companion change review.
- Frontend verification includes types, lint, formatting, unit coverage, production build and existing Playwright desktop/mobile tests. Those browser tests mock the API; they do not establish live browser-to-database correctness.
- Read paths included booking creation and rescheduling, availability rules and policies, calendar authorization, identity/session guards, shared form state, time conversion, background delivery/expiration and build/deployment configuration. The review went deepest on booking correctness and browser state.

Verdict: The confirmed findings above were corrected. Production readiness still requires deployment-specific verification and measured load targets.

Not checked: Every repository line, deployed AWS resources, live SMTP delivery, external penetration testing, fresh dependency advisories, production-size query plans and sustained multi-instance load. Existing company-level write serialization remains; its throughput has not been benchmarked.
