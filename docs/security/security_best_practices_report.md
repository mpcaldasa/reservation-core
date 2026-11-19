# Slotix Security Review Report

**Review date:** 2026-10-21
**Repositories reviewed:** `reservation-core` and sibling `../slotix-web`
**Scope:** code, migrations, Dockerfiles, Terraform, CI, supplied specifications, local tests, and a live npm advisory query. No production infrastructure or data was accessed.

## Remediation status

The findings below preserve the original audit baseline at backend commit `07d4382`. The user subsequently authorized fixes. The following status is authoritative for the remediated code:

| Finding | Implemented correction | Local verification |
| --- | --- | --- |
| SLO-SEC-001 | Possessive slug regex and an 80-character limit | Original and larger adversarial payloads complete without stack exhaustion. |
| SLO-SEC-002 | PostgreSQL account buckets, bounded request bodies, regional WAF with managed and IP rate rules, block alarm | Concurrent rate-limit test, body-limit tests, and mocked Terraform plan pass. AWS behavior remains a staging check. |
| SLO-SEC-003 | AES-256-GCM delivery envelopes, separate required encryption secret, legacy undelivered-token revocation | Encryption/tampering/wrong-key tests and SMTP-mocked invitation/recovery integration tests pass. |
| SLO-SEC-004 | Mandatory JWT secret, rejection of legacy/weak values, issuer/audience validation, 15-minute default TTL | JWT tests and full identity integration suite pass. |
| SLO-SEC-005 | DTO limits and PostgreSQL checks for new booking notes, cancellation reasons, and block reasons | Validation suite and PostgreSQL integration tests pass; historical oversized rows are preserved for operator review. |
| SLO-SEC-006 | UUID token identifiers and PostgreSQL revocations; frontend waits for server logout confirmation | Captured token rejected after logout; another session remains valid. Browser test covers failed logout and retry. |
| SLO-SEC-007 | Nonce CSP, Trusted Types, frame denial, permissions/referrer policies, HSTS at ALB | Production Nginx tests verify headers, nonce rotation, console health, and interaction on desktop/mobile. |
| SLO-SEC-008 | Vite 8.3.5 override within the existing minor release | Both npm audit graphs report zero vulnerabilities; full frontend verification passes. |
| SLO-SEC-009 | Immutable action SHAs, action-pin check, Dependabot for npm/actions/Docker | Pin checker and frontend CI syntax pass locally. |
| SLO-SEC-010 (follow-up) | Jackson 2.21.7/3.1.7 and Tomcat 11.0.27 patches | 119 tests and the complete quality gate pass; final image scan has zero HIGH/CRITICAL Java advisories. |
| SLO-SEC-011 (follow-up) | Remove unused Go helper from Java image; upgrade frontend Alpine packages; gate both images in CI | Final Trivy scans report zero HIGH/CRITICAL findings across 106 Ubuntu packages, 146 Java libraries, and 71 Alpine packages. |

Backend implementation commits: `0b0e606`, `3e189f6`, `379f0dc`, and `ae2aa53`. Frontend implementation commits: `aa7d95c` and `f17e986`. Additional evidence and rollout requirements are in [security-hardening.md](../../specs/security-hardening.md) and [security-remediation-verification.md](security-remediation-verification.md). No AWS resources were applied.

Updated verification: 119 backend tests passed with isolated PostgreSQL, 93.03% line coverage, 63.67% branch coverage, and zero high-priority SpotBugs findings. Frontend: 30 unit tests, 16 development E2E cases, and 18 production-container E2E cases passed. Terraform validation and its mocked production-security plan passed. The first Java archive-only Trivy invocation identified zero language files and is deliberately not treated as vulnerability evidence; the image scan is the relevant scan.

Follow-up image scanning found 13 HIGH/CRITICAL Java advisory entries and four Go helper entries in the original backend image, plus 45 HIGH/CRITICAL entries in the original frontend image. These are dependency-version findings; no application-level exploitation was claimed. Jackson/Tomcat patches, removal of an unused helper, and Alpine package upgrades removed all of these entries. The final scans include unfixed advisories and do not use a CVE suppression file. The HIGH/CRITICAL filter does not claim an absence of lower-severity advisories. Both image builds and scans passed locally; future CI runs must reproduce the result with current advisory databases.

Operational recommendations remain prerequisites for production approval: provision and rotate real secrets; apply and load-test the WAF/scaling configuration; verify SMTP and backup restoration; enforce GitHub branch rules; establish retention/access policies; and integrate MFA/SSO for privileged users. MFA/SSO is not implemented by the current password/JWT API. None of these external or additional identity capabilities is represented as completed by the local tests.

## Skill coverage and method

`security-best-practices` has native guidance for general JavaScript/TypeScript browser applications and for specific Express, React, Vue, Next.js, Django, Flask, FastAPI, and Go contexts. It does **not** natively support Java or Spring Boot, and Angular is not one of its named framework profiles. The Angular review applied its general JavaScript/TypeScript browser guidance. Java/Spring Boot, PostgreSQL, Terraform, Docker, GitHub Actions, AWS, and SMTP conclusions below are repository-specific manual traces and local verification; they are not native `security-best-practices` coverage.

Review steps included source/sink searches for SQL injection, DOM XSS, SSRF, path traversal, uploads, dynamic code execution, storage of browser secrets, authorization checks, and outbound HTTP clients; inspection of security configuration and migrations; inspection of AWS and CI configuration; and local verification:

| Verification | Result |
| --- | --- |
| `./mvnw -q test` | Passed: 112 tests, 0 failures, 0 errors, 0 skipped. PostgreSQL ran through Testcontainers. |
| `npm run typecheck` | Passed. |
| `npm run lint` | Passed. |
| `npm audit --omit=dev --json` | 0 production dependency vulnerabilities. |
| `npm audit --json` | 1 high and 1 low development dependency vulnerability, both through Vite under `@angular/build`. |
| Existing backend quality report | 93.07% line coverage, 63.22% branch coverage, 0 high-priority SpotBugs findings; this is not a CVE scan. |

## Prioritized findings

### SLO-SEC-001 — High — High confidence

**Unbounded nested slug regex can exhaust a Java request thread.**

- **Locations:** `src/main/java/com/slotix/reservationcore/company/CreateCompanyRequest.java:15-17`; `specs/quality-assessment.md:30`.
- **Attack scenario and preconditions:** A caller submits a company slug shaped like repeated `a-` fragments followed by `a`. The nested quantified regular expression recursively consumes stack frames. The documented Java 25 probe reproduced `StackOverflowError` with 10,001 characters. Company creation/onboarding is privileged, but MVC argument validation can execute before method-level authorization, so an authenticated user may be able to consume a request thread before the controller's authorization decision. Do not rely on that ordering until it is regression-tested.
- **Impact:** Repeated requests can cause availability degradation or process-level instability.
- **Evidence and validation:** The source regex is `^[a-z0-9]+(-[a-z0-9]+)*$`; the repository's own quality assessment records the standalone Java 25 reproduction. The destructive HTTP reproduction was intentionally not run against the shared local server.
- **Minimal correction:** Replace the regex with a flat, non-recursive validator and a domain limit enforced before costly validation. For example, parse character-by-character or use a bounded validation routine equivalent to `^[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9]))*$`, with a maximum slug length such as 80. Also set ingress request limits.
- **Regression test:** Submit the previous 10,001-character payload and a significantly larger adversarial payload through MockMvc as an ordinary authenticated user; assert a bounded-time `400`/validation response and no `StackOverflowError`.

### SLO-SEC-002 — High — High confidence

**Public identity endpoints lack a visible distributed rate-control layer.**

- **Locations:** `src/main/java/com/slotix/reservationcore/common/SecurityConfig.java:45-53`; `src/main/java/com/slotix/reservationcore/identity/PasswordResetService.java:23-26,46-54`; `infra/aws/network.tf:80-100`; `infra/aws/app.tf:210-256`.
- **Attack scenario and preconditions:** An internet attacker automates `POST /api/users/login`, `POST /api/platform/login`, password-reset, and invitation-acceptance traffic through the public ALB. Reset requests are capped only per known account, not by IP, network, device, or global queue. Neither a WAF resource nor an application rate limiter is defined in the reviewed infrastructure/code.
- **Impact:** Password spraying, credential stuffing, SMTP quota exhaustion, database/worker saturation, and degraded booking availability during traffic spikes.
- **Evidence and validation:** The ALB security group allows `0.0.0.0/0` on 80/443. Authentication/recovery routes are explicitly `permitAll`. Searches found no WAF, rate-based rule, bucket/token limiter, CAPTCHA, lockout, or distributed throttling implementation. The reset response correctly avoids account enumeration and imposes three requests per account per hour, which is insufficient as a perimeter control.
- **Minimal correction:** Add AWS WAF to the ALB with managed rules, request-size limits, and rate-based rules. Set separate burst and sustained limits for login, platform login, reset request, reset confirmation, invitation acceptance, and booking creation. Add an application-level, shared-store account/email limit and a progressive delay/temporary challenge for failed logins; never depend on per-process memory for a horizontally scaled service.
- **Regression test:** Infrastructure test asserts a WAF association and rate rules. API integration tests assert deterministic `429` for account/IP limits, reset requests remain non-enumerating, and a successful login clears only the appropriate failure counter.

### SLO-SEC-003 — High — High confidence

**Password-reset and invitation bearer tokens are stored in plaintext during email delivery.**

- **Locations:** `src/main/resources/db/migration/V18__generalize_identity_email_deliveries.sql:1-18`; `src/main/java/com/slotix/reservationcore/identity/PasswordResetService.java:100-104`; `src/main/java/com/slotix/reservationcore/identity/CompanyInvitationService.java:77-87`; `src/main/java/com/slotix/reservationcore/identity/JdbcIdentityEmailDeliveryStore.java:55-89`.
- **Attack scenario and preconditions:** A person or process with read access to PostgreSQL, a backup, an export, or an insufficiently separated operational database role reads `identity_email_deliveries.token` before the worker delivers or invalidates it. The token can reset a user's password for 30 minutes or accept an invitation for seven days.
- **Impact:** Account takeover or unauthorized account creation with the roles selected by an administrator.
- **Evidence and validation:** Domain token records store SHA-256 hashes, but the delivery table has `token text`; both services insert the original token. The worker reads it to construct the email link. The repository clears it after delivery and on expiration, replacement, or use, which limits but does not remove the exposure window.
- **Minimal correction:** Encrypt the delivery token at the application boundary with envelope encryption and a KMS key whose decrypt permission is separated from broad database-read access. Keep the hash-only domain token record; decrypt only immediately before SMTP submission, then clear ciphertext. Tighten database, backup, reporting, and log access to exclude this column.
- **Regression test:** Verify a queued row never contains the plaintext token, the worker can decrypt/send it, a reader without decrypt permission cannot recover it, and all terminal paths erase ciphertext.

### SLO-SEC-004 — Medium — High confidence

**The packaged application has a source-controlled default JWT signing secret.**

- **Locations:** `src/main/resources/application.properties:37-39`; `src/main/java/com/slotix/reservationcore/common/JwtService.java:22-27`; compensating AWS configuration at `infra/aws/app.tf:168-170`.
- **Attack scenario and preconditions:** A container is deployed outside the Terraform path, or its environment injection fails, and the application uses the known default HMAC key. An attacker with a known active user and company identifier can mint a valid bearer token for that identity.
- **Impact:** Bearer-token impersonation until the secret is changed. Current database re-resolution of role and credential version prevents role-claim-only escalation, but does not protect the impersonated identity.
- **Evidence and validation:** The default secret is committed in `application.properties`; `JwtService` accepts it without startup rejection. Terraform correctly injects `APP_JWT_SECRET` in the reviewed AWS task definition, so the finding is configuration-path dependent.
- **Minimal correction:** Remove the default value. Fail startup unless a high-entropy secret is supplied; validate minimum 256-bit entropy/length, add issuer and audience validation, document key rotation, and use separate secret values per environment.
- **Regression test:** A production-profile application context without `APP_JWT_SECRET` fails startup; a short/known key is rejected; tokens with wrong issuer/audience or an old rotated key are rejected.

### SLO-SEC-005 — Medium — High confidence

**Several persisted text inputs are unbounded at the API and database boundaries.**

- **Locations:** `src/main/java/com/slotix/reservationcore/booking/CreateBookingRequest.java:8-10`; `src/main/java/com/slotix/reservationcore/booking/CancelBookingRequest.java:1-4`; `src/main/java/com/slotix/reservationcore/booking/Booking.java:27-28,43-69`; `src/main/resources/db/migration/V9__create_bookings.sql:3-20`; `src/main/java/com/slotix/reservationcore/availability/CreateResourceBlockRequest.java:1`; `src/main/resources/db/migration/V7__create_resource_availability_tables.sql:22-34`.
- **Attack scenario and preconditions:** An authenticated user posts very large booking notes, cancellation reasons, or resource-block reasons. These values have no bean-validation maximum and persist to PostgreSQL `text` columns; staff views and API responses retrieve them later.
- **Impact:** Avoidable bandwidth, heap, database storage, audit/response amplification, and noisy operational records. Angular limits notes to 2,000 characters, but direct API callers bypass the UI.
- **Evidence and validation:** DTOs contain `String` fields without `@Size`; migration columns are unconstrained `text`. Searches found no server request-body limit or WAF size rule in the repository.
- **Minimal correction:** Apply explicit, business-appropriate limits consistently in DTO validation and database `CHECK (char_length(...) <= ...)` constraints. Set an edge/application JSON request-size cap, and use pagination/response limits for operational views.
- **Regression test:** Direct API requests just above each bound return `400`; concurrent oversized requests do not persist rows, audit records, notifications, or idempotency records.

### SLO-SEC-006 — Medium — High confidence

**Logout only clears browser memory; a stolen bearer token remains valid until expiry or credential-state change.**

- **Locations:** `../slotix-web/src/app/core/session.ts:58-70`; `src/main/java/com/slotix/reservationcore/common/JwtService.java:34-53`; `README.md:29` in the frontend repository.
- **Attack scenario and preconditions:** A token is copied from browser memory, an intercepted debugging session, or a compromised endpoint. When the user selects logout, the browser drops only its own reference; the copied token remains acceptable to the API for up to the configured 60 minutes unless the user is deactivated, their password changes, or their membership changes.
- **Impact:** Continued access as the user during the token lifetime after a user believes the session ended.
- **Evidence and validation:** `Session.clear()` changes local signals only. JWT validation checks signature, expiry, and credential version; no logout/revocation endpoint, token identifier, or deny-list appears in the reviewed code. Credential-version revalidation is an effective compensating control for password reset/deactivation.
- **Minimal correction:** For the high-security deployment target, issue short access tokens with a `jti` and introduce server-side revocation for logout/incident response, or adopt a managed OIDC provider with rotating refresh tokens, secure session cookies, and revocation. Avoid persisting bearer access tokens in local storage as a shortcut.
- **Regression test:** Log out, then present the captured token to a protected endpoint and assert rejection; verify revocation remains correct across two application instances.

### SLO-SEC-007 — Medium — Medium confidence

**The shipped web-container baseline lacks CSP and clickjacking protection.**

- **Locations:** `../slotix-web/deploy/nginx.conf:1-17`; `../slotix-web/src/index.html:1-13`.
- **Attack scenario and preconditions:** A future DOM injection bug, malicious extension, or content change has no browser-enforced CSP containment in the shipped Nginx configuration. A hostile site may frame the app if the production edge does not add `frame-ancestors` or `X-Frame-Options`.
- **Impact:** Increased blast radius for a future XSS bug and clickjacking risk around login/admin actions. No executable DOM sink or current XSS path was confirmed in this review.
- **Evidence and validation:** Nginx sets `X-Content-Type-Options` and `Referrer-Policy`, but no `Content-Security-Policy`, `frame-ancestors`, `X-Frame-Options`, `Permissions-Policy`, or HSTS header. An unseen CDN/edge could add them, so runtime deployment validation is still required.
- **Minimal correction:** Deliver a header CSP compatible with the Angular production bundle; start in report-only mode if needed, then enforce narrow `default-src`, `script-src`, `connect-src`, `img-src`, `style-src`, `base-uri`, `object-src 'none'`, and `frame-ancestors 'none'`. Add `X-Frame-Options: DENY` as compatibility defense, a restrictive Permissions-Policy, and HSTS at the HTTPS edge.
- **Regression test:** Container/ingress test fetches HTML and asserts headers. Browser test verifies application loading under the enforced policy and that framing is refused.

### SLO-SEC-008 — Medium — High confidence

**A vulnerable Vite version is present in the frontend development and CI dependency graph.**

- **Locations:** `../slotix-web/package.json:24-38`; `../slotix-web/package-lock.json`; `../slotix-web/.github/workflows/ci.yml:21-31`.
- **Attack scenario and preconditions:** A developer or CI process runs the affected Vite development server/build dependency, and an attacker reaches a vulnerable development endpoint or exploits editor-launch behavior. The npm advisory reports command-execution and local file-exposure issues for Vite 8.3.0 through 8.3.2.
- **Impact:** Developer workstation or CI compromise, followed by source/build artifact or credential exposure. This dependency is not in the production runtime image: `npm audit --omit=dev` returned zero findings.
- **Evidence and validation:** Live `npm audit --json` returned one high and one low finding through `@angular/build`'s Vite dependency. The frontend CI already runs `npm audit --audit-level=high`, so the current dependency state should fail that job until remediated.
- **Minimal correction:** Upgrade to an Angular build release that resolves Vite outside the affected range; do not force an incompatible transitive override without the Angular compatibility tests. Regenerate the lockfile, run the full frontend verification, and maintain a time-bounded exception only if no compatible release exists.
- **Regression test:** CI parses `npm audit --audit-level=high` successfully, and the resolved lockfile contains no affected Vite range.

### SLO-SEC-009 — Medium — High confidence

**Frontend CI executes mutable GitHub Action tags and has no dependency-update configuration.**

- **Locations:** `../slotix-web/.github/workflows/ci.yml:16-17,33`; absence of `../slotix-web/.github/dependabot.yml`.
- **Attack scenario and preconditions:** An upstream action tag is moved/compromised or an unreviewed dependency persists. The CI runner executes `actions/checkout@v4`, `actions/setup-node@v4`, and `actions/upload-artifact@v4` with repository access and dependency caches.
- **Impact:** Build-pipeline compromise or delayed remediation of vulnerable dependencies.
- **Evidence and validation:** The frontend workflow uses version tags rather than immutable commit SHAs. Unlike the backend, it has no Dependabot configuration. The workflow's token permissions are read-only, which reduces but does not eliminate impact.
- **Minimal correction:** Pin each action to a reviewed full commit SHA, add a policy/tool to verify action pins, and add Dependabot updates for npm and GitHub Actions. Keep branch protection requiring this workflow.
- **Regression test:** Actionlint/pin-policy test rejects mutable `uses:` references, and Dependabot configuration is validated in CI.

## Confirmed protective controls

- Passwords use BCrypt; request records redact passwords and tokens in `toString()`.
- JWTs have expiry and are revalidated against persisted active-user state, credential version, active company, active membership, and current roles on every authenticated request. Token role claims are not authoritative.
- Tenant reads/writes use company-bound service checks and repository predicates; integration tests cover private-resource visibility, cross-company access, role changes, and membership suspension.
- Booking creation combines a company lock, idempotency record, transactional audit/outbox writes, and a PostgreSQL GiST exclusion constraint for capacity-one conflicts. Concurrent integration tests cover double booking and lifecycle transitions.
- Password-reset responses are generic, reset tokens are single-use and hashed in their primary table, and password reset invalidates prior credential versions.
- No user-controlled HTML sink, `eval`, `new Function`, dynamic script source, `postMessage`, browser storage of bearer tokens, arbitrary server-side HTTP client, upload endpoint, or filesystem path handling was found in the reviewed source.
- AWS Terraform configures TLS 1.2/1.3 policy at the ALB, private ECS tasks, private encrypted RDS, Secrets Manager injection, and ECR scan-on-push. The backend container runs as a non-root user.

## Follow-up dependency findings

### SLO-SEC-010 — High dependency risk — High confidence

- **Status:** corrected and re-scanned. Confirmed affected artifact versions; no demonstrated application-level exploit.
- **Locations at baseline:** `pom.xml:5-9` inherited the Spring Boot 4.1.1 BOM, selecting Jackson 2.21.5/3.1.5 and Tomcat 11.0.24. The packaged image confirmed these exact artifacts in `/app/app.jar/BOOT-INF/lib/`.
- **Attack scenario and preconditions:** Jackson parser advisories can affect processing of adversarial JSON under their documented parser conditions. The three CRITICAL Tomcat entries require FORM/DIGEST/servlet security-constraint conditions absent from Slotix's current JWT/Spring Security path. Those missing preconditions invalidate an assertion of a confirmed authentication bypass in Slotix.
- **Impact:** parser availability risk and future container-authentication exposure if the affected mechanisms are enabled.
- **Evidence:** Trivy 0.76.1 reported CVE-2026-89407, CVE-2026-89425, CVE-2026-68497, CVE-2026-91776, CVE-2026-91777 and the three Tomcat entries CVE-2026-65182, CVE-2026-65905, CVE-2026-68525. [Apache's advisory list](https://tomcat.apache.org/security-11.html) and [Jackson's advisory list](https://github.com/FasterXML/jackson-core/security/advisories) were consulted as primary sources.
- **Correction:** retain Boot 4.1.1, override the Jackson BOMs to 2.21.7/3.1.7 and Tomcat to 11.0.27, and remove overrides after the Boot BOM supplies equivalent patches.
- **Regression evidence:** full backend verification, PostgreSQL integration suite, quality gate, container build, and final Trivy image scan pass. CI now fails HIGH/CRITICAL image and library advisories.

### SLO-SEC-011 — High dependency risk — High confidence

- **Status:** corrected and re-scanned. Confirmed affected runtime package versions; exploitation was not demonstrated.
- **Locations at baseline:** backend `Dockerfile:9-15` inherited `/usr/bin/pebble` with Go 1.27.1; frontend `../slotix-web/Dockerfile:1` inherited older Alpine packages, including OpenSSL, curl, pcre2, XML/image libraries, and nghttp2.
- **Attack scenario and preconditions:** the helper's network/TLS-related advisories would require execution of the helper; Slotix's direct Java entry point does not use it. Frontend package exploitation depends on each library's reachability through Nginx or its optional modules. Affected versions were present, but no single reachable application exploit was established.
- **Impact:** avoidable runtime dependency exposure and a release pipeline capable of shipping known vulnerable packages.
- **Evidence:** initial image scans reported four Go standard-library entries and 45 HIGH/CRITICAL Alpine package entries. The final backend image contains no Go helper target; the final Alpine target contains 71 scanned packages and zero HIGH/CRITICAL entries.
- **Correction:** remove the unused helper, upgrade Alpine packages during the frontend image build, and require image scans for both repositories. Base image and action references are pinned; current advisory databases remain mandatory.
- **Regression evidence:** rebuilt images pass Trivy; 18 browser tests pass against the patched web image. CI's action syntax and action-pin checks pass.

## Risks pending runtime validation

These are not reported as confirmed vulnerabilities because the needed evidence is outside the repositories.

| Area | What to verify before production |
| --- | --- |
| Edge controls | Deploy-time WAF association, rate limits, request-size limits, DDoS posture, header set, HTTPS/HSTS behavior, and whether the web app is served from the same origin. |
| Secrets | `APP_JWT_SECRET` is mandatory/unique per environment, bootstrap is disabled after use, IAM/KMS policies are least privilege, and secret rotation works without service outage. |
| Data protection | RDS backups/snapshots, log access, exports, CloudWatch retention/access, Terraform remote state encryption/locking, and data-retention/deletion processes. |
| Authentication | MFA/SSO rollout for platform administrators, breached-password policy, credential-stuffing monitoring, alert handling, and session revocation design. |
| Integrations | SMTP TLS/certificate validation, real mailbox delivery, link host allowlists, OTLP vendor settings, and outbound network egress restrictions. |
| Supply chain | Backend Maven dependency CVE scan, container image scan results, SBOM/provenance/signing, and remote CI branch-protection enforcement. |
| Resilience | Load, concurrency, WAF, database failover, backup restore, and incident-response exercises at expected high concurrency. |

## First five actions before production

1. Fix and regression-test the slug validator so adversarial input cannot exhaust the JVM stack.
2. Put AWS WAF and distributed identity/API rate limits in front of the public ALB; set alerting for blocked/rate-limited traffic.
3. Encrypt recovery and invitation delivery tokens at the application boundary, reduce access to delivery data, and test every cleanup path.
4. Remove the JWT fallback secret, require secret configuration at startup, establish key rotation and revocation, and require MFA/SSO for platform administrators.
5. Resolve the Vite advisory, pin frontend GitHub Actions by SHA, add dependency updates, and require the audit gate before release.

## Coverage and limitations

The review traced controllable HTTP/browser inputs into authorization, persistence, email, audit, infrastructure, and build impacts; it reviewed all source modules listed in the backend project map and the frontend's session/interceptor/routes/templates/configuration. Local test evidence is strong for tenant isolation and booking concurrency, but it does not replace a staging penetration test or load test.

Not verified: a deployed edge, AWS account state, remote Terraform state, actual IAM policies after apply, real email provider behavior, production logs/traces, live backup restoration, external DNS/certificates, downstream vulnerability status for Maven/container dependencies, or a production data classification/legal retention review. No finding in this report asserts complete system security.
