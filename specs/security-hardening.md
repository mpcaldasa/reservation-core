# Security hardening

## Input boundaries

Company slugs use possessive repetition, accept only lowercase alphanumeric segments separated by single hyphens, and are limited to 80 characters. Legal names, display names, and contact email addresses have explicit limits. Booking notes, cancellation reasons, and block reasons are limited to 2,000 characters at the HTTP boundary and for new database writes. Historical oversized rows are preserved by unvalidated PostgreSQL checks; operators must review and remediate them before validating the constraints.

`RequestValidationSecurityTest` submits the previously failing 10,001-character slug and a larger malformed slug under a bounded timeout. Integration tests use PostgreSQL Testcontainers.

## JWT sessions

`APP_JWT_SECRET` is mandatory. Generate at least 32 random bytes and encode them for secret injection; never use example values outside tests. Startup rejects short, low-diversity, or legacy development keys. Tokens default to 15 minutes, carry a UUID `jti`, issuer `slotix`, and audience `slotix-api`. Signature, issuer, audience, expiry, current credentials, and database revocation are validated on every request. Pre-hardening tokens are intentionally invalidated.

`POST /api/logout` revokes the current bearer token in PostgreSQL, so other application instances immediately observe the revocation. Other sessions remain active. Expired revocation rows are removed periodically. During signing-key rotation, replace the signing secret and restart all tasks; all previously issued tokens become invalid. Coordinate rotation with clients and incident response.

Local startup now requires `APP_JWT_SECRET`, for example a locally generated `openssl rand -base64 48` value. Store deployed values in Secrets Manager. Testcontainers tests inject a test-only key.

## Identity email secrets

When email is enabled, `APP_IDENTITY_DELIVERY_ENCRYPTION_KEY` must contain a Base64-encoded random 32-byte AES key (generate with `openssl rand -base64 32`). Keep it independent of the JWT key and database credentials, injected through a separate Secrets Manager secret. Identity delivery tokens are encrypted with AES-256-GCM, random 96-bit nonces, and an authenticated purpose/version context. Plaintext exists only in application memory and SMTP messages. Domain records retain only the token hash.

V23 revokes undelivered legacy plaintext links and clears their delivery copies; users must request new recovery links and administrators must recreate undelivered invitations. Sent links whose plaintext was already cleared remain governed by their original expiry. Roll out this change with old workers stopped. Drain all deliveries before rotating the encryption key; changing it while encrypted deliveries remain makes those deliveries unreadable. Separate database/backup reader permissions from access to this secret, audit secret reads, and restrict SMTP access. RDS encryption alone does not protect against a database reader.

## Abuse controls

PostgreSQL-backed rate buckets apply across all tasks: 20 login attempts per normalized account per five minutes, shared by company and platform login, and five recovery requests per normalized email per hour. Buckets contain SHA-256 identifiers and expire after a day. Counters commit in independent transactions even when authentication fails. Limits apply equally to unknown accounts. The existing three actual reset emails per active account/hour remains in place. Account limits can temporarily inconvenience a targeted user; WAF IP rules provide the complementary perimeter control.

POST/PUT/PATCH request bodies are capped at 32 KiB before parsing, including chunked bodies. The filter buffers only the bounded body. Return `413 INVALID_REQUEST` when exceeded. WAF and connection timeouts must also bound slow clients before they reach application threads.

## AWS perimeter and capacity

The ALB is associated with a regional AWS WAF ACL containing Common Rule Set, IP reputation, 100 identity requests per IP/five minutes, and 3,000 total requests per IP/five minutes. WAF counters are approximate perimeter safeguards; account counters are enforced by PostgreSQL. Shared NATs may require tuned limits after load tests. Common Rule Set's body-size rule intentionally caps public bodies at 8 KiB on ALB. Sampled requests are disabled to avoid storing credentials; CloudWatch metrics and an alarm record blocked traffic without request contents.

Production starts at least two private Fargate tasks and scales toward 60% CPU up to `max_task_count` (default ten). Each task has ten database connections; ensure RDS has headroom for all tasks, workers, migrations, and administration before raising the cap. The database default is `db.t4g.medium`. `desired_count=0` disables task autoscaling for initial provisioning or maintenance. Terraform applies may reset desired count to its configured baseline. Benchmark the existing company-level booking lock under tenant-heavy workloads before selecting a throughput target.

ALB sends HSTS, frame denial, and nosniff. SMTP on AWS requires STARTTLS and certificate identity verification. Email-enabled tasks must receive a separate `identity_delivery_encryption_secret_arn`; startup rejects a missing/malformed encryption key. Terraform's mocked plan tests assert WAF rules, HSTS, private encrypted database, and two-task production capacity without using AWS credentials.

## Dependency and image gates

The container CI job scans the released runtime image with pinned Trivy 0.76.1 and fails on HIGH/CRITICAL advisories, including unfixed issues. The first local image scan found vulnerable Jackson 2.21.5/3.1.5 and Tomcat 11.0.24 artifacts. Pinned BOM overrides update Jackson to 2.21.7/3.1.7 and Tomcat to 11.0.27 while retaining Spring Boot 4.1.1. Remove these overrides when the upstream Boot BOM includes equivalent patched versions. The container removes the unused `pebble` certificate helper, whose bundled Go runtime also had security advisories; the existing direct Java entry point does not execute this helper.

Library advisory presence is distinct from a demonstrated application exploit. The flagged Tomcat FORM/DIGEST authentication conditions are not used by the JWT/Spring Security API, but patched artifacts are still required. No broad CVE exclusions are used to pass the gate.
