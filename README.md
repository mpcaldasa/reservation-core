# Slotix reservation core

Slotix is a modular Spring Boot monolith for multi-company booking of rooms, courts, equipment, vehicles, people, and services. It uses Java 25, PostgreSQL, Flyway, Spring Security with JWT, and Testcontainers.

## Local development

1. Start PostgreSQL with `docker compose up -d postgres`.
2. Start the API with `./mvnw spring-boot:run`.
3. Run the integration suite with `./mvnw verify`. Tests start their own PostgreSQL container and do not use the development database.

The local `application.properties` contains development credentials. Supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `APP_JWT_SECRET` in deployed environments.

## API and operations

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Booking creation: `POST /api/v1/bookings` with an `Idempotency-Key` header
- Booking cancellation: `POST /api/v1/bookings/{bookingId}/cancel`
- Calendar: `GET /api/v1/calendar?from=<instant>&to=<instant>`

Booking creation locks the company row, checks the active policy and availability, and writes the booking, allocation, idempotency record, audit entry, and outbox event in one transaction. PostgreSQL also rejects overlapping active bookings for a resource with capacity one. Notification events are staged as pending email deliveries; a mail provider is not configured yet.

## Container image

Build with `docker build -t slotix-reservation-core:local .`. The image runs as an unprivileged user and listens on port 8080. The Dockerfile builds with Java 25 and packages the application without running tests; CI runs the Testcontainers suite before building the image.

## AWS infrastructure

Terraform configuration is in `infra/aws`. It creates an HTTPS Application Load Balancer, private ECS Fargate tasks, private RDS PostgreSQL, ECR, CloudWatch logs, a private versioned S3 asset bucket, and narrowly scoped network access. The task receives the database password from the RDS managed secret and the JWT signing key from a supplied Secrets Manager secret.

Use a separate AWS account and encrypted S3 state bucket for each environment. Create an ACM certificate and a Secrets Manager secret containing a long random JWT signing key before applying. DNS must point the API hostname to the `api_load_balancer_dns` output. The certificate must cover that hostname.

For a first deployment:

1. Run `terraform -chdir=infra/aws init` with S3 backend settings for `bucket`, `key`, `region`, `encrypt=true`, and `use_lockfile=true`.
2. Run `terraform -chdir=infra/aws apply` with `environment`, `certificate_arn`, `jwt_secret_arn`, `image_tag`, and `desired_count=0`.
3. Build and push the Docker image to `image_repository_url` using the chosen immutable tag.
4. Apply again with `desired_count=1` or greater, then check `/actuator/health/readiness` through the load balancer.

Terraform provisions infrastructure only. It does not create the remote state bucket, ACM certificate, DNS record, JWT secret, or send email. Each environment needs its own state key and variables. Review AWS costs before applying, especially NAT Gateway, load balancer, and RDS.
