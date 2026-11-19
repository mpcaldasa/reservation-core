# Product improvements delivery

Requested scope: implement the product improvements, prioritizing named customers, resource calendars and reminders. Use the normal local database for browser acceptance evidence, preserve QA data, and use isolated PostgreSQL Testcontainers for integration tests. New frontend flows retain the existing Spanish locale; engineering artifacts use English.

## Delivery sequence

- [ ] Tenant-scoped customer search, customer profile and booking history; staff selection by name.
- [ ] Names in booking responses, authenticated workspace metadata and authorized company switching.
- [ ] Daily resource columns, compact working hours/zoom and a quick booking detail panel with preserved list state.
- [ ] Configurable reminders, concurrency-safe dispatch, cancellation/rescheduling safety and local SMTP evidence.
- [ ] Useful daily reservation/approval/occupancy summaries based on persisted data.
- [ ] Recurring reservations and waiting-list enrollment/notifications.
- [ ] Optional public company booking portal with email verification and abuse limits.
- [ ] Google Calendar connection/synchronization; external verification requires application OAuth credentials.
- [ ] Secure browser session restoration and easier company discovery/sign-in.
- [ ] Additional calendar provider integration after Google, subject to credentials.
- [ ] Cross-role real browser acceptance run, HTTP evidence and screenshots.

Payments are deferred at the user's explicit request on 2026-11-19: prioritize Google Calendar; payments later. Do not add fake checkout controls or represent unconfigured external providers as tested.

Each functional block must pass relevant checks and `git diff --check`, then be committed and pushed independently. Existing unrelated files (`angular.json` and `.DS_Store`) remain excluded.
