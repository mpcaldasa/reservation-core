# Slotix reservation core

Slotix is a modular Spring Boot monolith for multi-company booking of rooms, courts, equipment, vehicles, people, and services. It uses Java 25, PostgreSQL, Flyway, Spring Security with JWT, and Testcontainers.

Engineering conventions and implemented behavior are documented in [specs](specs/README.md).

Application observability uses structured JSON logs, Micrometer metrics, OpenTelemetry traces, and OTLP exporters. AWS deployments use CloudWatch and an ADOT sidecar by default; Datadog OTLP intake is configurable with Secrets Manager credentials. Local tracing can be viewed in Jaeger. See the [observability guide](specs/observability.md) for setup, sampling, and cost notes.

## Local development

1. Start PostgreSQL with `docker compose up -d postgres`.
2. Start the API with `./mvnw spring-boot:run`.
3. Run the integration suite with `./mvnw verify`. Tests start their own PostgreSQL container and do not use the development database.

The local `application.properties` contains development credentials. Supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `APP_JWT_SECRET` in deployed environments.

## First platform administrator and company onboarding

For the first startup, set `APP_BOOTSTRAP_PLATFORM_ADMIN_ENABLED=true`, `APP_BOOTSTRAP_PLATFORM_ADMIN_EMAIL`, and `APP_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD` through the deployment's secret configuration. `APP_BOOTSTRAP_PLATFORM_ADMIN_FULL_NAME` is optional. Bootstrap creates one global platform administrator only when no platform administrator assignment exists. It does not reset existing credentials. Disable the bootstrap flag and remove its password configuration after initialization.

Authenticate with `POST /api/platform/login` using `email` and `password`. Its token can call `POST /api/v1/companies/onboarding` with `company` details and an `administrator` containing either `existingUserId` or `newUser` (`email`, `password`, `fullName`). The response contains the active company and its first administrator membership. The company administrator can then authenticate through `POST /api/users/login` with the company identifier.

Company administrators manage existing users through `/api/v1/companies/{companyId}/memberships`: paginated `GET`, `POST` to add a user, `PUT /{membershipId}/roles`, and `POST /{membershipId}/suspend` or `/activate`. Role changes take effect for existing JWTs, and the last active administrator cannot be removed. See the [identity specification](specs/company-onboarding-and-memberships.md) and [implementation progress](specs/implementation-plan.md).

## API and operations

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Booking creation: `POST /api/v1/bookings` with an `Idempotency-Key` header
- Booking cancellation: `POST /api/v1/bookings/{bookingId}/cancel`
- Booking rescheduling: `PUT /api/v1/bookings/{bookingId}/reschedule` with `startAt` and `endAt`
- Resource editing: `PUT /api/v1/companies/{companyId}/resources/{resourceId}` (company administrator)
- Resource deactivation: `POST /api/v1/companies/{companyId}/resources/{resourceId}/deactivate`
- Schedule and block changes: `PUT` or `DELETE` on `/api/v1/companies/{companyId}/resources/{resourceId}/availability-rules/{ruleId}` and `/blocks/{blockId}` (company administrator)
- Booking policies: `/api/v1/companies/{companyId}/booking-policies` supports administrator list, create, update, activate, and deactivate operations
- Staff check-in: `POST /api/v1/bookings/{bookingId}/check-in`
- Staff completion: `POST /api/v1/bookings/{bookingId}/complete`
- Staff no-show: `POST /api/v1/bookings/{bookingId}/no-show` after the booking end time
- Calendar: `GET /api/v1/calendar?from=<instant>&to=<instant>`

Booking creation locks the company row, checks the active policy and availability, and writes the booking, allocation, idempotency record, audit entry, and outbox event in one transaction. PostgreSQL also rejects overlapping active bookings for a resource with capacity one. Notification events are staged as email deliveries.

Company administrators and booking managers can check in confirmed bookings within their reserved interval, then complete them when resource use ends. Completion releases occupied capacity. Repeating an already applied operation preserves its original actor and timestamp without duplicating audit or notification events. See the [booking lifecycle specification](specs/booking-lifecycle.md).

Pending bookings expire after 30 minutes by default. Configure `app.booking.pending-timeout` with an ISO-8601 duration; the expiration worker polls every 60 seconds and processes up to 100 bookings per batch. Customers can reschedule their own pending or confirmed bookings when the current booking policy permits cancellation and its notice window remains open. Staff can reschedule any pending or confirmed booking. Idempotency keys expire after 24 hours by default, configurable through `app.booking.idempotency.ttl`.

## Email notifications

Email sending is disabled until SMTP is configured. Set `APP_NOTIFICATION_EMAIL_ENABLED=true`, `APP_NOTIFICATION_EMAIL_FROM`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, and, when authentication is required, `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD`. Configure `spring.mail.properties[mail.smtp.auth]` and `spring.mail.properties[mail.smtp.starttls.enable]` for the provider. Connection, read, and write timeouts default to five seconds.

The worker sends English booking creation, confirmation, rejection, cancellation, rescheduling, expiration, check-in, and completion messages to the booking customer. Each delivery permits five total attempts by default, with exponential retry delays. Claim tokens prevent a replaced worker from overwriting another worker's result. SMTP executes outside database transactions. Delivery records retain the attempt count and a stable error code. A crash after the SMTP server accepts a message but before the database marks it sent can cause a duplicate; email delivery is at least once.

Tune `app.notification.email.max-attempts`, `app.notification.email.batch-size`, and `app.notification.email.lease-seconds` when required. Enabled email configuration is validated at startup. See the [notification specification](specs/notification-delivery.md) for limits, recovery rules, and acceptance coverage.

## Container image

Build with `docker build -t slotix-reservation-core:local .`. The image runs as an unprivileged user and listens on port 8080. The Dockerfile builds with Java 25 and packages the application without running tests; CI runs the Testcontainers suite before building the image.

## AWS infrastructure

Terraform configuration is in `infra/aws`. It creates an HTTPS Application Load Balancer, private ECS Fargate tasks, private RDS PostgreSQL, ECR, CloudWatch logs, a private versioned S3 asset bucket, and narrowly scoped network access. The task receives the database password from the RDS managed secret and the JWT signing key from a supplied Secrets Manager secret.

The committed `.terraform.lock.hcl` records the verified provider version and checksums. Review provider upgrades as explicit dependency changes.

Use a separate AWS account and encrypted S3 state bucket for each environment. Create an ACM certificate and a Secrets Manager secret containing a long random JWT signing key before applying. DNS must point the API hostname to the `api_load_balancer_dns` output. The certificate must cover that hostname.

For a first deployment:

1. Run `terraform -chdir=infra/aws init` with S3 backend settings for `bucket`, `key`, `region`, `encrypt=true`, and `use_lockfile=true`.
2. Run `terraform -chdir=infra/aws apply` with `environment`, `certificate_arn`, `jwt_secret_arn`, `image_tag`, and `desired_count=0`.
3. Build and push the Docker image to `image_repository_url` using the chosen immutable tag.
4. Apply again with `desired_count=1` or greater, then check `/actuator/health/readiness` through the load balancer.

Terraform provisions infrastructure only. It does not create the remote state bucket, ACM certificate, DNS record, JWT secret, or send email. Each environment needs its own state key and variables. Review AWS costs before applying, especially NAT Gateway, load balancer, and RDS.

To enable email on ECS, supply `smtp_enabled=true`, `smtp_host`, `smtp_port`, `smtp_from`, and the HTTPS `invitation_acceptance_url` and `password_reset_acceptance_url` for the frontend. Optionally set `smtp_username` and `smtp_password_secret_arn`; the password secret must contain a plain string. The sender address must be authorized by the SMTP provider. SMTP stays disabled when `smtp_enabled=false`.

For the first platform administrator on ECS, supply `platform_admin_bootstrap_enabled=true`, `platform_admin_bootstrap_email`, and `platform_admin_bootstrap_password_secret_arn`. `platform_admin_bootstrap_full_name` is optional. The password secret contains a plain string and is injected directly into the task. Disable bootstrap after the first initialization; the optional secret injection and its execution-role access are then removed from the task configuration.

### Resource visibility

Private resources are available only to company administrators and booking managers. Customers cannot browse their schedules, blocks, availability, or policy assignments, or create new bookings for them. Staff can book private resources for active members; customers retain access to their own existing bookings. PUBLIC and MEMBERS resources currently both require authenticated company membership. See [resource visibility](specs/resource-visibility.md) and [implementation progress](specs/implementation-plan.md).
