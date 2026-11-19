# Booking reminders

Company administrators configure `GET/PUT /api/v1/companies/{companyId}/reminders` with `enabled` and `minutesBefore` (1–10080). Defaults are disabled and 60 minutes. Enabling requires configured email delivery. Customer contact data and notification configuration remain tenant scoped.

Every 30 seconds, a bounded worker selects up to 100 confirmed upcoming bookings within their company's notice window. PostgreSQL row locks with `SKIP LOCKED` and a unique occurrence index prevent concurrent workers or notice changes from enqueuing duplicates for the same booking start. Reminders use the existing outbox, delivery leases and retry policy.

Before claiming email work, obsolete reminders become `SKIPPED`: cancellation, changed start time, disabled reminders, inactive company/user or removed/inactive customer membership. Rescheduling to a new start creates a new occurrence. Pending approval bookings are excluded. Existing reminders cannot be recalled after SMTP sending has begun. SMTP delivery remains at least once: a process failure after provider acceptance and before the database success record may cause a duplicate retry.

Integration tests use isolated PostgreSQL Testcontainers. They cover administrator checks, validation, missing transport, competing schedulers, notice changes, cancellation and rescheduling. Live acceptance uses the normal QA database and a local SMTP receiver, not a mocked mail sender; it does not prove Internet email delivery or inbox placement.
