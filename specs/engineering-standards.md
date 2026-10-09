# Engineering standards

## Architecture

Slotix is a Spring Boot modular monolith organized by capability: company, identity, resource, availability, policy, booking, audit, and notification. Existing capabilities use controllers, application services, and repositories. Avoid introducing a generic framework or reorganizing unrelated capabilities as part of a feature.

Introduce an interface at a boundary when it isolates an external system or infrastructure concern and permits meaningful behavior tests. The notification application service depends on `EmailSender` and `NotificationDeliveryStore`. `SmtpEmailSender` and `JdbcNotificationDeliveryStore` implement those ports. `BookingEmailTemplate` owns presentation, and `NotificationRetryPolicy` owns retry timing.

Use constructor injection and immutable records for data crossing these boundaries. Keep application decisions out of controllers and SQL details out of orchestration services. Prefer small methods with explicit names and narrow exception handling. Introduce patterns to address a concrete responsibility or failure mode.

## Transactions and tenancy

Business writes, audit entries, and outbox events that describe the same operation must commit atomically. Network calls must occur outside database transactions. Concurrent operations must rely on database locking, versioning, or constraints rather than an in-memory lock shared by only one process.

Authenticated API operations must derive the company from the authenticated principal and verify access. Background operations must carry company identity from persisted data and join related records with company identity where available.

## Database evolution

Add a new Flyway migration for a schema change. Do not rewrite an existing migration that could have been applied. Put durable invariants in database constraints, and add indexes for the access patterns actually used by the feature. Worker transactions should be short and skip rows already locked by another worker.

## Errors and operations

Classify expected external failures separately from persistence failures and programming errors. Preserve enough state to recover interrupted work. Operational logs may include delivery identifiers, attempt numbers, and stable error codes; they must not include SMTP credentials, recipient addresses, or message bodies.

Bind operational configuration to typed, validated properties. Optional integrations must remain disabled until configured. Document delivery guarantees and recovery limitations alongside the implementation.

## Verification

Use unit tests for independent policies and failure classification. Use real PostgreSQL through Testcontainers for migrations, SQL constraints, locking, and transaction behavior. Tests must control scheduled processing and bound waits in concurrent scenarios.

Run `./mvnw verify` before completing an implementation change. Run Terraform validation when changing infrastructure configuration. Update specification acceptance coverage when adding or changing behavior.
