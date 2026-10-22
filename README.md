# Slotix reservation core

Slotix is a multi-tenant booking platform for organizations that need to manage reservations for rooms, courts, equipment, vehicles, people, and services. This repository contains the Spring Boot API; the Angular web application lives in the sibling `../slotix-web` repository.

The backend is a modular Spring Boot 4.1.1 application built with Java 25. It uses PostgreSQL 16, Flyway-managed schema migrations, Spring Security with JWT authentication, and Testcontainers for PostgreSQL integration tests. The Angular frontend uses Angular 22 and proxies `/api` requests to the local backend during development.

Engineering conventions and implemented behavior are documented in [specs](specs/README.md).

Application observability uses structured JSON logs, Micrometer metrics, OpenTelemetry traces, and OTLP exporters. AWS deployments use CloudWatch and an ADOT sidecar by default; Datadog OTLP intake is configurable with Secrets Manager credentials. Local tracing can be viewed in Jaeger. See the [observability guide](specs/observability.md) for setup, sampling, and cost notes.

## Local development

### Project map

- `src/main/java`: API and domain modules, including identity, companies, resources, availability, policies, bookings, audit, and notifications.
- `src/main/resources/db/migration`: versioned Flyway database migrations. The application validates the schema at startup; do not edit an already applied migration.
- `src/test/java`: unit and API integration tests. Integration tests start an isolated PostgreSQL container through Testcontainers.
- `specs/`: behavior, architecture, quality, and operational specifications.
- `infra/aws/`: Terraform configuration for the AWS deployment.
- `../slotix-web/`: sibling Angular frontend. See its [README](../slotix-web/README.md) for frontend-specific architecture and workflows.

### Prerequisites

Install Java 25, Docker with Docker Compose, Node.js `^22.22.3`, `^24.15.0`, or `>=26.0.0`, and npm 11. The backend Maven wrapper downloads the pinned Maven version on first use. The local workflow uses ports 5432 (PostgreSQL), 8080 (API), and 4200 (Angular); make sure they are available.

### Run the frontend and backend together

From this repository, start the local PostgreSQL database:

```sh
docker compose up -d postgres
```

Start the backend in one terminal:

```sh
export APP_JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. The default local database is `slotix` on `localhost:5432`, with the development credentials configured in `src/main/resources/application.properties`. Flyway applies pending migrations at startup.

In another terminal, install and start the frontend:

```sh
cd ../slotix-web
npm ci
npm start
```

Open `http://localhost:4200`. The Angular development server proxies `/api` to `http://localhost:8080`, so the browser uses the local API without extra CORS setup. Check API readiness at `http://localhost:8080/actuator/health/readiness`; explore the API at `http://localhost:8080/swagger-ui.html`.

For an empty local database, create the first platform administrator by setting the bootstrap variables before starting the backend. Use a local-only password and disable the bootstrap flag after initialization:

```sh
export APP_BOOTSTRAP_PLATFORM_ADMIN_ENABLED=true
export APP_BOOTSTRAP_PLATFORM_ADMIN_EMAIL='admin@example.test'
export APP_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD='replace-with-a-local-password'
export APP_BOOTSTRAP_PLATFORM_ADMIN_FULL_NAME='Local Platform Admin'
./mvnw spring-boot:run
```

Then sign in at `http://localhost:4200/platform/login` and use the platform UI to onboard a company and its first administrator. Company users sign in with their company identifier. Bootstrap creates an administrator only if none exists; it does not reset existing credentials. For the API flow and all variable names, see [First platform administrator and company onboarding](#first-platform-administrator-and-company-onboarding). Do not use the example credentials outside a disposable local environment.

Stop the application processes with `Ctrl+C`. Stop PostgreSQL with `docker compose stop postgres`; `docker compose down` also removes the container but preserves its named database volume.

## Tests and developer checks

With PostgreSQL, the backend, and Angular running as described above, run the local full-stack smoke tests with:

```sh
python3 -B scripts/local_stack_smoke.py
```

These checks verify backend readiness and OpenAPI, that Angular serves its app shell, and that the Angular `/api` proxy forwards to the backend. The proxy check sends deliberately invalid credentials and expects an authentication rejection; it does not require or modify a user account. Override the defaults with `SLOTIX_API_URL` and `SLOTIX_FRONTEND_URL` when the services use different local addresses.

Run backend unit tests with:

```sh
./mvnw test
```

Run the complete backend verification, including PostgreSQL integration tests (Docker required), with:

```sh
./mvnw verify
```

Run the backend quality profile and its CI quality gate with:

```sh
./mvnw -Pquality clean verify
python3 -B scripts/ci/quality_gate.py
```

For the Angular application, run commands from `../slotix-web` after `npm ci`:

```sh
npm test                 # unit tests
npm run test:coverage    # unit tests with coverage
npm run e2e              # Playwright end-to-end tests (mocked API)
npm run typecheck
npm run lint
npm run build
npm run verify           # typecheck, lint, formatting, coverage, build, and E2E
```

Install the Playwright browser once with `npx playwright install chromium` if it is not already available. `npm run e2e:real` exercises a dedicated test company against a real backend; follow the frontend README and never point it at production data. The frontend README also documents contract checks, dependency audits, configuration, and the test-account setup.

The local `application.properties` contains development database credentials. Supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and a unique random `APP_JWT_SECRET` in deployed environments. JWT signing configuration is mandatory and has no fallback key. See [security hardening](specs/security-hardening.md) for revocation, encrypted identity emails, rate limits, migration impact, and production rollout requirements.

### Quality assessment

Run `./mvnw -Pquality clean verify` to execute all tests and package verification with JaCoCo coverage, SpotBugs plus Find Security Bugs, PMD maintainability analysis, and CPD duplication detection. Docker is required for PostgreSQL integration tests. Tool versions are pinned in the Maven profile.

Coverage is available at `target/site/jacoco/index.html`; static reports are `target/spotbugsXml.xml`, `target/pmd.xml`, and `target/cpd.xml`, with PMD and CPD HTML in `target/site/`. After Maven verification, run `python3 -B scripts/ci/quality_gate.py` to apply the same quality gate used by CI: at least 90% line coverage and 60% branch coverage, a nonempty test run with no failed or skipped tests, no high-priority SpotBugs findings, and complete analyzer reports without processing errors. Medium/low SpotBugs findings, PMD warnings, and duplication remain visible for review. Maven verification must also pass; the report gate does not replace it.

CI publishes a GitHub Actions summary and uploads reports as `backend-quality-reports`, including test diagnostics when a run fails, for 14 days. Pipeline verification runs actionlint and quality-gate regression tests; the merge gate requires backend, pipeline, infrastructure, and container jobs to succeed. External actions are pinned to verified commits, and Dependabot proposes weekly Maven, Docker, and GitHub Actions updates. This update configuration is not a dependency vulnerability scan.

See the [quality assessment](specs/quality-assessment.md) for the measured baseline, reviewed findings, and verification limits.

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

Email also requires `identity_delivery_encryption_secret_arn`, a separate Secrets Manager secret containing a Base64-encoded random 32-byte encryption key. V23 revokes undelivered legacy plaintext identity links; reissue those links after rollout. Stop old workers before migrating and drain encrypted deliveries before key rotation. Local email requires `APP_IDENTITY_DELIVERY_ENCRYPTION_KEY` as well.

The public ALB now uses a regional WAF with managed rules, IP-based limits, and a blocked-request alarm. Production starts at least two private tasks and scales toward 60% CPU, up to `max_task_count` (ten by default). Size RDS and its connection budget for this maximum; the default instance is `db.t4g.medium`. Apply no infrastructure until staging has validated workload-specific limits and costs. See the [security hardening specification](specs/security-hardening.md).

For the first platform administrator on ECS, supply `platform_admin_bootstrap_enabled=true`, `platform_admin_bootstrap_email`, and `platform_admin_bootstrap_password_secret_arn`. `platform_admin_bootstrap_full_name` is optional. The password secret contains a plain string and is injected directly into the task. Disable bootstrap after the first initialization; the optional secret injection and its execution-role access are then removed from the task configuration.

### Resource visibility

Private resources are available only to company administrators and booking managers. Customers cannot browse their schedules, blocks, availability, or policy assignments, or create new bookings for them. Staff can book private resources for active members; customers retain access to their own existing bookings. PUBLIC and MEMBERS resources currently both require authenticated company membership. See [resource visibility](specs/resource-visibility.md) and [implementation progress](specs/implementation-plan.md).
