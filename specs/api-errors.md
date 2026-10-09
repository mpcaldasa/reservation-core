# API error contract

All handled API errors use a stable JSON envelope:

```json
{
  "status": 409,
  "code": "POLICY_ASSIGNMENT_OVERLAP",
  "error": "The requested period overlaps an existing policy assignment",
  "fields": {}
}
```

- `status` is the HTTP status and always matches the response status.
- `code` is a stable, machine-readable identifier. Clients should branch on this value rather than the message.
- `error` is a concise English explanation suitable for logs or user-facing translation layers.
- `fields` maps invalid request field names to validation messages. It is an empty object when the error is not field validation.

Business errors use `ApiException` with a specific `ApiErrorCode`. Request validation, authentication, authorization, data conflicts, and booking state transitions each have a dedicated code. Existing `ResponseStatusException` paths use a stable fallback code derived from their HTTP status while those workflows are migrated to specific codes. Unexpected failures are logged server-side and return `INTERNAL_ERROR` without exposing exception details.

## Policy assignment errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `INVALID_POLICY_ASSIGNMENT_INTERVAL` | 422 | The end is not after the assignment start |
| `POLICY_ASSIGNMENT_OVERLAP` | 409 | The requested period overlaps another assignment |
| `POLICY_ASSIGNMENT_CANNOT_BE_EXTENDED` | 409 | A close operation attempts to extend an assignment |
| `POLICY_ASSIGNMENT_NOT_FUTURE` | 409 | Removal was requested for an assignment that has started |
| `ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_CHANGE` | 409 | Active reservations prevent closing or replacing the assigned policy |
| `ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_REMOVAL` | 409 | Active reservations prevent removing the assignment |
| `POLICY_ASSIGNMENT_NOT_FOUND` | 404 | The assignment does not exist for this resource and effective start |
| `ACTIVE_RESERVATIONS_PREVENT_RESOURCE_DELETION` | 409 | Active reservations prevent deleting the resource |
| `RESOURCE_CAPACITY_LOCKED_BY_ACTIVE_RESERVATIONS` | 409 | Capacity cannot change while active reservations remain |

## Company and identity errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `COMPANY_SLUG_ALREADY_EXISTS` | 409 | A company with the requested slug already exists |
| `COMPANY_NOT_FOUND` | 404 | The company does not exist |
| `COMPANY_INACTIVE` | 422 | The company is not active for membership administration |
| `COMPANY_ONBOARDING_STATE_INVALID` | 422 | The company cannot be onboarded from its current state |
| `COMPANY_ADMIN_ALREADY_ASSIGNED` | 409 | The company already has an active administrator |
| `ADMINISTRATOR_IDENTITY_INVALID` | 422 | Exactly one existing or new administrator identity is required |
| `ACTIVE_ADMINISTRATOR_NOT_FOUND` | 404 | The selected administrator account does not exist or is inactive |
| `MEMBERSHIP_PAGE_INVALID` | 400 | The membership page or page size is outside the supported range |
| `MEMBERSHIP_ALREADY_EXISTS` | 409 | The user already has an active membership in the company |
| `MEMBERSHIP_NOT_FOUND` | 404 | The membership does not exist in the current company |
| `ACTIVE_USER_NOT_FOUND` | 404 | The selected user account does not exist or is inactive |
| `LAST_COMPANY_ADMIN_REQUIRED` | 409 | The operation would leave the company without an active administrator |
| `USER_ALREADY_EXISTS` | 409 | The email address is already assigned to an account |
| `PASSWORD_BYTE_LIMIT_EXCEEDED` | 422 | The password exceeds the 72-byte UTF-8 limit |
| `EMAIL_DELIVERY_UNAVAILABLE` | 503 | Email delivery is disabled, so no invitation was created |
| `INVITATION_NOT_FOUND` | 404 | The invitation token is invalid or unavailable |
| `INVITATION_EXPIRED` | 410 | The invitation has expired |
| `INVITATION_ALREADY_ACCEPTED` | 409 | The invitation was already consumed |
| `INVITATION_ALREADY_EXISTS` | 409 | A pending invitation already exists for the email in this company |
| `PASSWORD_RESET_NOT_FOUND` | 404 | The password reset token is invalid or unavailable |
| `PASSWORD_RESET_EXPIRED` | 410 | The password reset token has expired |
| `PASSWORD_RESET_ALREADY_USED` | 409 | The password reset token has already been consumed |
| `PLATFORM_USER_NOT_FOUND` | 404 | The global user account does not exist |
| `PLATFORM_USER_PAGE_INVALID` | 400 | The account page or page size is outside the supported range |
| `LAST_PLATFORM_ADMIN_REQUIRED` | 409 | The operation would leave the platform without an active administrator |
| `PLATFORM_ROLE_NOT_FOUND` | 404 | The user does not have the requested platform administrator role |
| `PLATFORM_ADMIN_REQUIRES_ACTIVE_USER` | 422 | Platform administrator privileges require an active account |

## Policy and availability errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `BOOKING_POLICY_ASSIGNED_CANNOT_EDIT` | 409 | Assigned policies cannot be edited |
| `BOOKING_POLICY_HAS_ACTIVE_ASSIGNMENTS` | 409 | A current or future assignment prevents policy deactivation |
| `BOOKING_POLICY_INVALID` | 422 | Policy values are invalid or do not align with the slot increment |
| `POLICY_NOT_FOUND` | 404 | The active policy does not exist in this company |
| `AVAILABILITY_RULE_NOT_FOUND` | 404 | The availability rule does not exist in the current resource |
| `AVAILABILITY_RULE_INVALID` | 422 | The recurring availability interval or date range is invalid |
| `RESOURCE_BLOCK_NOT_FOUND` | 404 | The resource block does not exist in the current resource |
| `RESOURCE_BLOCK_INTERVAL_INVALID` | 422 | The block requires a start, an end after the start, and a reason |
| `AVAILABILITY_DURATION_INVALID` | 400 | The requested availability duration must be positive |

## Booking errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `IDEMPOTENCY_KEY_INVALID` | 400 | The idempotency key is missing, blank, or too long |
| `IDEMPOTENCY_KEY_REUSED` | 422 | The key was reused for a different request |
| `IDEMPOTENT_BOOKING_NOT_FOUND` | 409 | The idempotency key references a booking that no longer exists |
| `BOOKING_NOT_FOUND` | 404 | The booking does not exist in the current company |
| `CUSTOMER_MEMBERSHIP_REQUIRED` | 422 | The customer does not have an active company membership |
| `BOOKING_INTERVAL_INVALID` | 422 | A new booking interval is invalid |
| `BOOKING_SLOT_UNAVAILABLE` | 409 | The requested time slot is unavailable |
| `CALENDAR_RANGE_INVALID` | 400 | The calendar range must be positive and no longer than 90 days |
| `BOOKING_POLICY_UNAVAILABLE` | 422 | No active policy covers the booking interval |
| `RESOURCE_INACTIVE` | 422 | The resource cannot accept or move bookings while inactive |
| `BOOKING_STATE_INVALID` | 422 | The booking cannot transition from its current state |
| `BOOKING_ALLOCATION_NOT_FOUND` | 409 | The booking's resource allocation is missing |
| `BOOKING_ALLOCATION_STATE_MISMATCH` | 409 | The booking and allocation statuses do not match |
| `CUSTOMER_CANCELLATION_NOT_ALLOWED` | 422 | The customer is outside the policy cancellation window |
| `CANCELLATION_REASON_REQUIRED` | 422 | Staff cancellations require a reason |
| `CUSTOMER_RESCHEDULING_NOT_ALLOWED` | 422 | The customer is outside the policy rescheduling window |
| `RESCHEDULE_INTERVAL_INVALID` | 422 | The requested replacement interval is invalid |
| `RESCHEDULE_POLICY_CONSTRAINT_VIOLATION` | 422 | The replacement duration violates the active policy |

## Security and request errors

Authentication failures return `AUTHENTICATION_REQUIRED` or `INVALID_CREDENTIALS`; authorization failures return `ACCESS_DENIED`. Invalid JSON, path values, query values, or bean validation return `INVALID_REQUEST` or `VALIDATION_FAILED`. Validation responses include field details under `fields`. Unsupported methods and content types return `METHOD_NOT_ALLOWED` and `UNSUPPORTED_MEDIA_TYPE`.

## Operations and compatibility errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `RESOURCE_NOT_FOUND` | 404 | The requested resource does not exist in the current company or context |
| `NOTIFICATION_DELIVERY_NOT_FOUND` | 404 | The notification delivery does not exist in the current company |
| `NOTIFICATION_DELIVERY_NOT_REPLAYABLE` | 409 | Only failed notification deliveries can be replayed |
| `NOTIFICATION_STATUS_INVALID` | 400 | The requested notification status filter is invalid |
| `OPERATIONAL_PAGE_INVALID` | 400 | The audit or notification page parameters are outside the supported range |
| `DATA_CONFLICT` | 409 | A persisted record conflicts with the requested operation |
| `REQUEST_CONFLICT` | 409 | Compatibility fallback for an unmapped HTTP conflict |
| `REQUEST_REJECTED` | Varies | Compatibility fallback for an otherwise unmapped response status |
| `INTERNAL_ERROR` | 500 | An unexpected server failure occurred; details are kept out of the response |

## Verification status

The envelope is applied by the global API exception handler and the Spring Security entry points. Business services use `ApiException` with specific codes; the global `ResponseStatusException` handler remains as a compatibility fallback. Unit coverage verifies the envelope, fallback codes, and internal-error detail suppression; API integration coverage verifies booking, assignment, resource, company, identity, and pagination error codes.
