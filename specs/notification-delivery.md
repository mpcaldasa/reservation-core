# Notification delivery

Status: implemented for booking creation, confirmation, rejection, cancellation, check-in, and completion.

## Scope and boundaries

Booking operations append an outbox event in the booking transaction. `NotificationQueueWorker` turns each event into one email delivery. `EmailDeliveryWorker` claims a delivery, renders its content, invokes the `EmailSender` port, and records the result through `NotificationDeliveryStore`.

SMTP is implemented by `SmtpEmailSender`; persistence and locking are implemented by `JdbcNotificationDeliveryStore`. A plain email message contains its recipient, subject, and body without depending on JavaMail. The template uses English and formats times in the booking timezone.

## Requirements

| ID | Requirement |
| --- | --- |
| NTF-001 | A booking outbox event produces at most one delivery, enforced by its unique `outbox_event_id`. Staging and marking the event processed occur in one transaction. A missing or inconsistent booking fails that staging transaction and leaves the event unprocessed. |
| NTF-002 | Claiming a delivery atomically changes it to `SENDING`, increments its attempt count, and assigns a new UUID claim token and expiry time. Competing workers skip locked rows. |
| NTF-003 | Result updates require both the delivery identifier and its current claim token. A replaced claim cannot record success or failure. |
| NTF-004 | SMTP executes after the claim transaction commits and outside any caller database transaction. |
| NTF-005 | Failed attempts become `FAILED` and receive a retry time. Delays are 1, 2, 4, 8, then 16 minutes, capped at 16 minutes. |
| NTF-006 | The attempt limit applies to both ordinary retries and recovery of expired claims. An expired final claim becomes terminal `FAILED` with `DELIVERY_LEASE_EXPIRED`. |
| NTF-007 | SMTP failure is recorded as `SMTP_SEND_FAILED`; invalid event content is recorded as `INVALID_NOTIFICATION_CONTENT`. Database failures propagate and leave the claim available for expiry recovery. |
| NTF-008 | Successful delivery becomes `SENT`, records `sent_at`, and clears claim ownership and the previous error. |
| NTF-009 | Email is disabled by default. Enabling it requires a valid sender address and an available SMTP sender. Invalid retry, batch, or lease configuration fails startup. |
| NTF-010 | Logs and persisted errors use identifiers and stable error codes rather than provider exception messages, credentials, or email contents. |

## State and recovery

| Current state | Trigger | Result |
| --- | --- | --- |
| `PENDING` | Due and below the attempt limit | `SENDING` with a fresh token |
| `FAILED` | Retry due and below the attempt limit | `SENDING` with a fresh token |
| `SENDING` | SMTP accepts the message and the token still owns the claim | `SENT` |
| `SENDING` | Transport or content failure and the token still owns the claim | `FAILED` with a retry time |
| `SENDING` | Lease expires below the attempt limit | Reclaimed as `SENDING` with a new token |
| `SENDING` | Lease expires at the attempt limit | Terminal `FAILED` |
| `SENT` | Any scheduled pass | Unchanged |

`FAILED` is terminal when its attempt count reaches the configured limit. Recovery and result recording clear claim ownership when leaving `SENDING`. A database constraint requires claim token and expiry to be present exactly while `SENDING`.

## Configuration

| Property | Default | Constraint |
| --- | --- | --- |
| `app.notification.email.enabled` | `false` | Explicitly enable SMTP delivery |
| `app.notification.email.from` | Required when enabled | Nonblank email address |
| `app.notification.email.max-attempts` | `5` | 1 through 20 total attempts |
| `app.notification.email.batch-size` | `10` | 1 through 100 per scheduled pass |
| `app.notification.email.lease-seconds` | `120` | 30 through 3600 seconds |
| `app.notification.email.delay-ms` | `5000` | Scheduled delay between completed passes |
| `app.notification.email.initial-delay-ms` | `5000` | Delay before the first email pass |
| `app.notification.queue-delay-ms` | `5000` | Delay between staging passes |
| `app.notification.queue-initial-delay-ms` | `5000` | Delay before the first staging pass |

SMTP connection, read, and write timeouts default to five seconds. The lease must comfortably exceed the provider call duration; SMTP timeouts are per operation and do not impose a total message deadline.

## Guarantees and limitations

Delivery uses at-least-once processing. If SMTP accepts a message and the process stops before acknowledging it, recovery can send a duplicate. Claim tokens protect persisted results; they cannot revoke an SMTP request already running. Retried or concurrent deliveries may arrive out of event order.

The recipient is captured during staging. Booking details and company display name are read when a delivery is claimed. Event type determines the message action; the template does not infer an event from the current booking status. Immutable content snapshots and delivery ordering remain outside the implemented scope.

Platform administrators can inspect paginated deliveries at `GET /api/platform/operations/notification-deliveries?status=FAILED&page=0&size=20` and replay a failed delivery with `POST /api/platform/operations/notification-deliveries/{deliveryId}/replay`. Replay resets attempts, clears the prior error, and makes the row immediately eligible for the normal worker. Only `FAILED` rows can be replayed; the action is recorded in the audit log. Operational audit history is available through the platform administrator endpoint `GET /api/platform/operations/audit`, with optional company, action, entity type, and entity ID filters.

## Acceptance coverage

| Scenario | Test |
| --- | --- |
| Idempotent staging, SMTP failure, delayed retry, and successful acknowledgement | `NotificationDeliveryIntegrationTest.retriesSmtpFailureAndRecordsTheSuccessfulAttempt` |
| SMTP suspends a caller transaction | `NotificationDeliveryIntegrationTest.sendsOutsideTheCallerDatabaseTransaction` |
| Expired ownership cannot overwrite the new owner's result | `NotificationDeliveryIntegrationTest.rejectsAcknowledgementsFromAReplacedClaim` |
| Expired final attempt becomes terminal | `NotificationDeliveryIntegrationTest.expiredFinalAttemptBecomesTerminalFailure` |
| Failed final SMTP attempt is not retried | `NotificationDeliveryIntegrationTest.finalSmtpFailureIsNotRetriedAgain` |
| Inconsistent tenant identity leaves the outbox event unprocessed | `NotificationDeliveryIntegrationTest.tenantMismatchDoesNotSilentlyProcessAnOutboxEvent` |
| Concurrent workers claim a delivery only once | `NotificationDeliveryIntegrationTest.concurrentWorkersCannotClaimTheSameDelivery` |
| Database acknowledgement failure is not classified as SMTP failure | `EmailDeliveryWorkerTest.databaseAcknowledgementFailureDoesNotBecomeAnSmtpFailure` |
| Supported English templates and booking timezone | `BookingEmailTemplateTest` |
| Exponential delay and upper bound | `NotificationRetryPolicyTest` |
| Disabled integration, typed defaults, and invalid enabled configuration | `NotificationEmailConfigurationTest` |
