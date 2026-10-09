# Company onboarding and membership management

Status: implemented for platform initialization, company onboarding, direct member provisioning, email invitations and acceptance, membership roles, suspension, and activation.

## Initial platform access

Platform bootstrap is disabled by default. For a first startup, supply `APP_BOOTSTRAP_PLATFORM_ADMIN_ENABLED=true`, `APP_BOOTSTRAP_PLATFORM_ADMIN_EMAIL`, and `APP_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD`. `APP_BOOTSTRAP_PLATFORM_ADMIN_FULL_NAME` defaults to `Platform Administrator`.

The startup initializer obtains a PostgreSQL transaction advisory lock, checks whether any platform administrator role already exists, and creates one user, one `PLATFORM_ADMIN` assignment, and an audit entry in one transaction. Concurrent application starts produce one administrator. An existing assignment makes initialization a no-op; supplied credentials are not used to reset an account or grant another administrator. Invalid settings or an email belonging to another user abort initialization without partial records. Disable bootstrap and remove its password configuration after initialization.

`POST /api/platform/login` accepts `email` and `password`. Only an active user with a persisted platform role can authenticate through this endpoint. Its token has no company context and grants platform operations only. The existing `POST /api/users/login` requires `companyId`, `email`, and `password`, plus an active user, company, and membership.

On ECS, optional Terraform variables `platform_admin_bootstrap_enabled`, `platform_admin_bootstrap_email`, `platform_admin_bootstrap_full_name`, and `platform_admin_bootstrap_password_secret_arn` configure the same initializer. The password is injected from a plain-string Secrets Manager secret. Bootstrap is disabled by default, and its secret access is included in the execution role only when enabled.

## Onboarding API

| Endpoint | Role | Input | Result |
| --- | --- | --- | --- |
| `POST /api/v1/companies/onboarding` | `PLATFORM_ADMIN` | `company` and `administrator` | HTTP 201 with an active company and its first administrator membership |
| `POST /api/v1/companies/{companyId}/initial-administrator` | `PLATFORM_ADMIN` | `administrator` fields directly | HTTP 200; initializes a pending or active company that has no active administrator |

`company` has the existing `CreateCompanyRequest` fields: `legalName`, `displayName`, `slug`, and `contactEmail`. The `administrator` object must specify exactly one of:

- `existingUserId`: UUID of an active existing user.
- `newUser`: `email`, `password`, and `fullName` for a new global identity.

Onboarding a new company creates the company, optional user, `COMPANY_ADMIN` membership, and audit records atomically. Linking an existing user preserves its password and other company memberships. Duplicate company slugs or existing email addresses return HTTP 409; callers use `existingUserId` to reuse an identity. Missing or inactive users return HTTP 404. Invalid identity selection returns HTTP 400. New passwords require 8 through 72 characters and at most 72 UTF-8 bytes.

Initializing an existing company locks the company row and rejects an existing active administrator with HTTP 409. Suspended or archived companies cannot use this initialization endpoint. A pending company becomes active. If the target user already has a membership, initialization preserves existing roles, adds `COMPANY_ADMIN`, and activates that membership.

Legacy company creation and activation endpoints remain available under `/api/companies`, protected by platform authorization. Their application decisions and auditing now reside in `CompanyService`.

## Company administration API

Base path: `/api/v1/companies/{companyId}/memberships`. All operations require an active company membership with `COMPANY_ADMIN`.

| Method and suffix | Input | Result |
| --- | --- | --- |
| `GET` | `page=0`, `size=20` | Stable page containing `items`, `page`, `size`, `totalElements`, and `totalPages` |
| `POST` | `userId` and nonempty `roles` | HTTP 201; adds an active existing user |
| `PUT /{membershipId}/roles` | Nonempty `roles` | HTTP 200; replaces the full company role set |
| `POST /{membershipId}/suspend` | No body | HTTP 200; suspends company access |
| `POST /{membershipId}/activate` | No body | HTTP 200; restores company access for an active user |

Page must be nonnegative and size must be 1 through 100. Ordering is by membership creation time and identifier. Responses include membership and user identifiers, email, full name, membership status, roles, join date, creation date, and update date. Password hashes and passwords are never returned.

`POST /api/users` retains its existing HTTP 200 contract and accepts the registration fields plus `companyId` and company roles. `CompanyMembershipService` now coordinates global user creation and company membership creation in one transaction. Duplicate users return HTTP 409 rather than silently replacing credentials.

`POST /api/v1/companies/{companyId}/memberships/invitations` requires a company administrator and accepts an email plus nonempty company roles. It returns HTTP 202 and queues a seven-day invitation email in the same transaction. Email delivery must be enabled or the request returns `EMAIL_DELIVERY_UNAVAILABLE` without persisting an invitation. Duplicate pending invitations and existing global accounts return HTTP 409. The message links to the configured frontend URL in `APP_IDENTITY_INVITATION_ACCEPTANCE_URL`.

`POST /api/invitations/accept` is public and accepts the emailed token, a new password, and a full name. Tokens contain 256 bits of random data; only their SHA-256 hashes are stored with invitations. The transactional email outbox holds the token only until delivery succeeds, acceptance occurs, or the invitation expires. Acceptance creates the global user, company membership, and audit records atomically. Tokens expire after seven days and can be accepted once.

## Password recovery

`POST /api/password-resets` always returns HTTP 202 with the same message whether an active account exists or email delivery is disabled. For an active account with email enabled, it creates a 30-minute single-use token and queues its delivery. At most three reset emails can be requested per account in an hour; a later allowed request revokes prior pending tokens. The token is hashed in the database; its temporary outbox copy is removed after delivery, replacement, use, expiration, or exhausted retries.

`POST /api/password-resets/confirm` accepts a token and a new password. On success, the password hash and credential version change in one transaction, all pending reset tokens are revoked, and an audit record is written without logging secrets. JWTs carry the credential version; changing the password immediately invalidates previously issued access tokens. The frontend completion URL is configured with `APP_IDENTITY_PASSWORD_RESET_ACCEPTANCE_URL`.

## Global account administration

`GET /api/platform/users` is available to persisted platform administrators and returns stable pages of account details and platform roles without credential fields. Page size is limited to 100. `POST /api/platform/users/{userId}/deactivate` and `/reactivate` change the global account state, increment its credential version, and write audit records only when the state changes. Deactivation blocks authentication across all companies; reactivation does not restore previously issued JWTs.

Platform administrators can grant and revoke the `PLATFORM_ADMIN` role with `POST` and `DELETE /api/platform/users/{userId}/platform-admin`. Grants require an active account. A PostgreSQL transaction advisory lock serializes bootstrap, deactivation, and role changes so the last active platform administrator cannot be deactivated or removed concurrently.

## Requirements and concurrency

| ID | Requirement |
| --- | --- |
| IDM-001 | Only a persisted platform administrator can onboard companies or assign their initial administrator. Company administrators cannot grant platform roles. |
| IDM-002 | Mutations verify the actor's current company role after acquiring the company row lock. Membership lookup includes both membership and company identifiers. |
| IDM-003 | The last active company administrator with an active user account cannot be suspended or demoted. Such operations return HTTP 409. Concurrent removals serialize on the company row. |
| IDM-004 | Linking an existing identity preserves its credentials and membership in other companies. Duplicate current memberships return HTTP 409. |
| IDM-005 | Repeating role replacement, suspension, or activation when nothing changes preserves the update timestamp and appends no duplicate audit entry. |
| IDM-006 | Every applied membership mutation and its audit snapshot commit in one transaction. Failed onboarding or registration leaves no partial company, user, or membership. |
| IDM-007 | Nonempty role sets are enforced by request validation and the domain model. Roles are copied on input and exposed as immutable sets. |
| IDM-008 | Global active-email uniqueness and current membership uniqueness remain database constraints. Migration V14 adds optimistic versioning and an index for company membership lookup and paging. |
| IDM-009 | JWT signatures, expiry, subject, credential version, and optional company context identify the request. Persisted user state, company state, membership state, credential version, and current roles determine authorization on each request; role claims are informational. |
| IDM-010 | Suspended memberships, inactive users or companies, and removed platform assignments invalidate the relevant authentication context with HTTP 401. An authenticated user without the required role receives HTTP 403. |
| IDM-011 | Company suspension affects that company context; suspending one membership does not block the same user's other active company memberships. Reactivation allows an unexpired token to authenticate again. |
| IDM-012 | Credential records redact passwords in their string representation. Audits and responses exclude credentials. Persistence outages propagate rather than being classified as invalid JWTs. |
| IDM-013 | Invitation tokens contain at least 256 bits of randomness, are stored as hashes, expire after seven days, and can be accepted once. |
| IDM-014 | Invitation creation and delivery enqueue commit together. Delivery uses expiring claims and bounded retries; plaintext tokens are removed after delivery, acceptance, expiration, or exhausted attempts. |
| IDM-015 | Invitation acceptance locks company before invitation, then creates the user, membership, and audit entries atomically. |
| IDM-016 | Password reset requests return an identical response regardless of account existence; tokens expire after 30 minutes and can be used once. |
| IDM-017 | Password changes increment the credential version so previously issued JWTs stop authenticating immediately. |
| IDM-018 | Global account status and platform role changes are audited, and account status changes invalidate existing credentials across all company contexts. |
| IDM-019 | A transaction advisory lock serializes platform administrator bootstrap, deactivation, and role changes to preserve at least one active platform administrator. |

## Boundaries and limitations

`UserProvisioningService` owns identity creation and hashing. `IdentityAuthenticationService` owns credential authentication and current authority resolution. `CompanyOnboardingService` coordinates initial company access; `CompanyMembershipService` coordinates subsequent company membership changes. Controllers delegate application decisions to these services. Password encoder configuration is independent of the security filter configuration.

The bootstrap advisory lock coordinates the first global administrator when no company row exists yet. Subsequent company administration uses company row locks and membership versioning. The last-administrator rule is enforced by application services; direct administrative SQL must preserve it.

Refresh tokens remain outside this implementation. Current authority resolution adds database reads to authenticated requests; caching with reliable invalidation is future work.

## Acceptance coverage

- `CompanyOnboardingApiIntegrationTest`: bootstrap uniqueness and concurrency, invalid initialization, new and existing administrator identities, immediate login, atomic rollback, legacy company initialization, duplicate slugs, UTF-8 password limits, and persisted platform authorization.
- `CompanyMembershipApiIntegrationTest`: linking existing users, paginated company isolation, current roles for existing tokens, suspensions and reactivation, last-administrator protection, concurrent demotions and duplicate links, registration conflicts, inactive identity states, and signed role-claim rejection.
- `CompanyInvitationApiIntegrationTest`: invitation and password reset delivery, token redaction and single-use acceptance, generic responses for unknown accounts, old-session invalidation, duplicate invitations, invalid tokens, and retryable SMTP failures.
- `PlatformUserAdministrationApiIntegrationTest`: paginated account listing, account deactivation and reactivation, invalidation of old JWTs, platform role grant and revocation, and protection of the last active administrator.
- `CompanyMembershipTest`: immutable role ownership, nonempty roles, idempotent timestamps and join date, and credential redaction.
- `PlatformBootstrapConfigurationTest`: opt-in startup configuration, default full name, and initializer wiring.
