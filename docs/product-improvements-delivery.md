# Product improvements delivery

The first delivery prioritizes named customers, resource calendars and reminders. Browser acceptance uses the normal local PostgreSQL database and preserves QA data. Integration tests use isolated PostgreSQL Testcontainers. Frontend copy retains the Spanish locale; engineering artifacts use English.

## Delivered

- Tenant-scoped customer search by name/email/phone, company-local contact profiles and paged booking history. Staff select named customers when booking; customers cannot search other people.
- Booking responses include customer/resource names. Authenticated workspace metadata provides company/person names and authorized company switching with roles resolved again for the target company. Target timezone loads before opening booking pages. Both desktop and mobile expose the company selector.
- Day/week/resource calendar views, up to four resource columns per page, filter-aware columns, readable zoom, a keyboard-accessible native-dialog preview and filtered reservation/approval/status counts.
- Administrator-controlled reminder settings and paged delivery history. Outbox deduplication and PostgreSQL row locks protect competing schedulers. Cancellation/rescheduling/inactive identities suppress obsolete reminders before claiming delivery. Live QA received ten reminder messages through local SMTP.
- Google Calendar OAuth/PKCE, encrypted tenant-bound refresh credentials, restricted secondary-calendar scope and manual one-way booking export. Missing configuration is explicitly disabled. The user confirmed on 2026-11-19 that no Google credentials are available and requested documentation of pending external verification.
- Cross-role browser acceptance, HTTP/curl evidence and screenshots; desktop/mobile regressions and automated accessibility checks.

## Remaining scope

- Google OAuth and event operations against an actual test account; automatic synchronization and external busy-time import. The first integration is manual and does not claim two-way synchronization.
- Secure session restoration after page reload and company discovery during sign-in. Access tokens remain in memory; no secret was moved into browser local storage. Initial generic sign-in still requires a company identifier unless an invitation supplies its context.
- Recurring booking series and waiting-list enrollment/notifications.
- Optional public company booking portal with verified contact information and abuse controls.
- Occupancy/utilization analytics with a denominator derived from actual opening hours; the delivered counters intentionally describe only the filtered reservation set. Per-resource working-hour shading/configuration is also pending.
- Outlook after Google. Payments remain deferred at the user's explicit request: prioritize Google Calendar, payments later.

No placeholder controls advertise these remaining functions as implemented.

## Verification and artifacts

Current authentication remains Spring Security/JWT and the verified database is local PostgreSQL. Supabase has not been integrated or configured in either repository.

Backend: 136 tests passed, including PostgreSQL integration, tenant isolation, concurrent booking/reminder dispatch and credential-boundary checks. Targeted regressions passed after the final quality and reminder-history changes. Frontend: type checking, lint, format, contract generation/check, unit tests, coverage/build and desktop/mobile browser regressions. The browser regression suite uses fixtures and is separate from the live backend/database acceptance run.

Acceptance evidence is stored outside both repositories in `/Users/mapis/.codex/visualizations/2026/10/01/01a0f7db-806d-7132-b93b-f33a6cb842d4/slotix-customers-2026-11-19`. It includes a report, screenshot gallery, actual HTTP responses/curl examples and a private local QA access guide. QA contacts, reservations, memberships and notification history remain available through the frontend.

Each backend block and the integrated frontend delivery receive checks, an English commit and a push. Existing unrelated `.DS_Store` files and the frontend `angular.json` change remain excluded.
