# Security remediation verification

Date: 2026-10-21, America/Bogota.

The original nine audit findings received implementation changes. Follow-up runtime scanning identified vulnerable Java and image-package versions; those were also patched. Local verification is complete for the checks below. Production approval still requires the external checks listed at the end.

## Changes and commits

| Repository | Commit | Change |
| --- | --- | --- |
| reservation-core | `0b0e606` | Non-recursive slug validation, bounded input fields, database text checks |
| reservation-core | `3e189f6` | Mandatory JWT key, issuer/audience, token revocation, encrypted identity emails, shared account limits, bounded request bodies |
| reservation-core | `379f0dc` | WAF rules, HSTS, encryption-secret injection, SMTP TLS verification, production task scaling, mocked infrastructure tests |
| reservation-core | `ae2aa53` | Patched Jackson/Tomcat, unused helper removal, image vulnerability CI gate |
| slotix-web | `aa7d95c` | Server-confirmed logout, same-origin configuration, nonce CSP/Trusted Types, safe access logs, patched Vite, pinned actions and Dependabot |
| slotix-web | `f17e986` | Alpine security updates and frontend image vulnerability gate |

Both repositories were pushed to their existing branches: backend `feat/multi-tenant-foundation`, frontend `main`. The pre-existing backend `.DS_Store` and frontend `angular.json` analytics change were excluded from commits.

## Executed checks

| Check | Observed result |
| --- | --- |
| `./mvnw -B -Pquality clean verify` | BUILD SUCCESS; 119 tests, zero failures/errors/skips; PostgreSQL Testcontainers |
| `python3 -B scripts/ci/quality_gate.py` | Passed; 93.03% line and 63.67% branch coverage; no high-priority SpotBugs findings |
| `python3 -B -m unittest discover -s scripts/ci -p 'test_*.py' -v` | Seven tests passed |
| Terraform 1.10.0 `fmt`, `validate`, and `test` in `infra/aws` | Valid configuration; mocked production-security plan passed without AWS credentials |
| `npm run verify` in slotix-web | Types, lint, formatting, build, 30 unit tests, and 16 desktop/mobile E2E cases passed |
| `npm audit --json` | Zero advisories across production and development dependencies |
| `node scripts/check-action-pins.mjs` | Passed |
| `npx playwright test --config playwright.security.config.ts` against patched Nginx image | 18 desktop/mobile cases passed |
| Production browser console/header probe at 1440x1000 and 390x844 | Nonblank page, valid title, form validation, CSP/Trusted Types, frame denial, referrer policy, changing per-response nonce, and no app console/page errors |
| Pinned actionlint container against both repositories | Passed |
| Backend and frontend Docker builds | Passed |
| Trivy 0.76.1 image scans with `--severity HIGH,CRITICAL --exit-code 1 --scanners vuln` | Both passed without CVE exclusions, including unfixed advisories |
| `git diff --check` | Passed for committed changes |

The Browser plugin was not available; the repository's Playwright workflow and a temporary Playwright console/header probe were used. The flow under test was production `/login` -> form validation -> login -> authenticated page -> logout/retry, with desktop and mobile profiles. Development E2E and production-container E2E use mocked API responses. Backend PostgreSQL integration tests independently verify real authorization, token revocation, encrypted recovery flow, rate buckets, and database constraints. Live SMTP and a deployed AWS edge were not represented as passed.

## Runtime scan evidence

| Artifact | Initial HIGH/CRITICAL entries | Final scanned packages | Final HIGH/CRITICAL entries |
| --- | ---: | --- | ---: |
| Backend Java libraries | 13 | 146 libraries | 0 |
| Backend Ubuntu packages | 0 | 106 packages | 0 |
| Backend unused Go certificate helper | 4 | Helper removed | 0 |
| Frontend Alpine runtime | 45 | 71 packages | 0 |

Final locally scanned artifact identifiers:

- Backend: `sha256:ebf40f34245834d056ff29c342c4e658033852577a83e2a01ba6e11a59663be6`
- Frontend: `sha256:76b557814d849b8d07134182783eda6a25adebf74c5303d7f806633cfda411c4`

The archive-only Trivy experiment did not identify Java files and was discarded as evidence. The image scans explicitly identified the nested Spring Boot JAR libraries. Zero HIGH/CRITICAL entries does not imply zero lower-severity, undisclosed, or configuration vulnerabilities. CI downloads current advisory databases and repeats the scans on each release image.

## Deployment requirements and remaining verification

1. Supply a unique random `APP_JWT_SECRET`. Existing tokens are invalid after the new claim requirements/signing configuration. The default TTL is 15 minutes.
2. Before enabling email, supply a separate Base64-encoded random 32-byte `APP_IDENTITY_DELIVERY_ENCRYPTION_KEY`, through `identity_delivery_encryption_secret_arn` on AWS. Stop old application workers before V23; reissue legacy undelivered identity links after migration. Do not rotate the encryption key while queued encrypted deliveries remain.
3. Review historical text rows before validating the V21 `NOT VALID` database checks. New writes are constrained immediately; historical data was not truncated or deleted.
4. Apply Terraform to staging and verify actual WAF limits, HSTS, alerts, IAM/secret access, SMTP TLS, task scaling, database connection limits, and backup restoration. No AWS apply occurred during this work. Use workload-specific load tests before claiming a throughput target.
5. Enforce branch protection and verify remote CI. The local checks do not establish GitHub account settings or cloud runtime state.
6. MFA/SSO for privileged users, data-retention/legal policies, and operational incident procedures remain additional production requirements. Native password/JWT login has no MFA/SSO integration yet; this capability is not represented as implemented by perimeter controls or the completed fixes.

## Primary references consulted

- [Angular security and CSP](https://angular.dev/best-practices/security)
- [AWS WAF rate-based rules](https://docs.aws.amazon.com/waf/latest/developerguide/waf-rule-statement-type-rate-based.html)
- [Apache Tomcat 11 security advisories](https://tomcat.apache.org/security-11.html)
- [Jackson Core security advisories](https://github.com/FasterXML/jackson-core/security/advisories)
- [Vite file-access advisory and patched versions](https://github.com/vitejs/vite/security/advisories/GHSA-rq7h-c2jc-7f22)
