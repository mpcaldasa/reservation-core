# Quality assessment

Measured on 2026-10-08 against backend commit `c622668` with the quality tooling introduced in the working tree. This is a baseline snapshot; regenerated reports are authoritative for subsequent changes.

## Executed checks

`./mvnw -B -Pquality clean verify` passed with Java 25 and real PostgreSQL through Testcontainers. All 112 tests across 20 suites passed, with zero failures, errors, or skipped tests. The final run includes JaCoCo 0.8.14, SpotBugs Maven 4.10.4.1 with Find Security Bugs 1.14.0, and Maven PMD 3.28.0 (PMD 7.17.0). Static analysis scans production sources; tests are exercised separately.

| Measurement | Result |
| --- | --- |
| Line coverage | 93.07% (2,270 covered, 169 missed) |
| Branch coverage | 63.73% (506 covered, 288 missed) |
| Instruction coverage | 89.20% |
| Method coverage | 88.41% |
| SpotBugs and security detector findings | 148 raw findings: 81 medium priority, 67 low priority |
| PMD findings | 130 raw findings |
| CPD duplicate groups | 1 group, 105 tokens / 15 lines |
| Terraform formatting and validation | Passed |
| Docker Compose configuration validation | Passed |
| Git whitespace validation | Passed |
| Actionlint workflow validation | Passed with version 1.7.11 pinned by image digest |
| Quality-gate regression tests | 7 passed |
| Quality-report gate | Passed |
| Container image build | Passed with `docker build --tag slotix-reservation-core:ci .` |

SpotBugs reported zero analysis errors and zero missing classes. PMD's `LoosePackageCoupling` rule is excluded because it requires an allowed-package mapping that is not defined; other selected rules remain enabled. The report gate now requires at least 90% line coverage and 60% branch coverage, no high-priority SpotBugs findings, no failing or skipped tests, and complete reports without analysis errors. Medium/low findings, PMD warnings, and duplication are reported for review. Tool execution errors and packaging failures also fail CI. A successful build therefore does not mean the warning backlog is empty.

## Reviewed priorities

1. **Slug validation robustness.** `CreateCompanyRequest.slug` uses `^[a-z0-9]+(-[a-z0-9]+)*$` without a length bound. A standalone Java 25 probe with `-Xss1m` and input `"a-".repeat(5000) + "a"` reproduced `StackOverflowError` at 10,001 characters. This confirms a regex robustness problem; HTTP-level exploitability and deployment stack limits were not tested. Use a non-backtracking equivalent and a domain-appropriate maximum length, and verify adversarial input. A size annotation alone does not establish validation ordering.
2. **Complex business methods.** PMD measured cyclomatic complexity of 22 and cognitive complexity of 33 in `AvailabilityService.slots`; cyclomatic complexity of 24 in `BookingService.create`; and cyclomatic complexity of 25, cognitive complexity of 16, and NPath complexity of 278 in `BookingReschedulingService.reschedule`. Extract cohesive decisions while preserving transaction and locking boundaries. PMD also flags `BookingService` for high coupling and concentrated responsibilities; this is a maintenance signal, not a demonstrated functional failure.
3. **Branch coverage.** Prioritize meaningful alternative-path tests in booking creation (31 missed branches), availability calculation (19), and rescheduling (18). `GlobalExceptionHandler` has 21.4% branch coverage despite strong overall line coverage. Do not inflate percentages with trivial entity/getter tests.
4. **Mutable collection exposure.** SpotBugs reports collection exposure in request/response records and `AuthenticatedIdentity.roles`. Review defensive copies for records whose collection ownership matters. Constructor references to Spring-managed services, JDBC templates, and registries also trigger exposure warnings; they are not equivalent to leaking mutable domain collections.
5. **Duplicated lifecycle guards.** CPD identifies shared code around `AvailabilityRuleService:107` and `ResourceBlockService:99`, including company membership, activity, role, and resource locking checks. Consider a narrow shared operation if these guards evolve together; preserve lock order and tenant checks.
6. **Minor dead assignment.** `CompanyInvitationService.lockActiveCompany` assigns a `Company` local that is never read. The repository call still performs essential locking and activity validation; remove only the unused assignment if addressing the warning.

## Security and warning interpretation

Find Security Bugs contributes 61 `SPRING_ENDPOINT` findings. These identify endpoints for review and do not prove missing authentication. The three SQL injection findings were reviewed: audit filters use internally selected constant column names with parameterized values; idempotency cleanup interpolates a constant batch size with a parameterized timestamp. No user-controlled SQL fragment was identified in those locations.

The hash comparison finding concerns booking request idempotency hashes, not a password or invitation-token comparison. The two log injection findings interpolate persisted UUID identifiers. The authorization-header finding identifies an untrusted input that is passed through JWT signature validation and persisted identity resolution. None of these findings alone establishes an exploitable vulnerability.

Ten nullability findings include JDBC `count(*)` unboxing and authenticated security-context assumptions. Inspect each call path before deciding whether it needs a defensive check. JPA reflective field initialization, Spring's security API exception signature, and validation constructors also account for warnings. No blanket suppression was introduced to make the report appear clean.

## Reproduction and report locations

Run `./mvnw -Pquality clean verify`, followed by `python3 -B scripts/ci/quality_gate.py`. Open `target/site/jacoco/index.html` for coverage, `target/site/pmd.html` and `target/site/cpd.html` for source analysis, and `target/spotbugsXml.xml` for bytecode/security findings. CI uploads these artifacts together with Surefire test reports and `target/quality-summary.md` as `backend-quality-reports`, retained for 14 days. The summary is also published directly on the GitHub Actions run.

Infrastructure checks: `docker compose config --quiet`, `terraform fmt -check -recursive infra/aws`, and `terraform -chdir=infra/aws validate -no-color` after provider initialization.

## Scope limits

This assessment does not include a dependency CVE database scan, container vulnerability scan, load/performance tests, browser testing, staging deployment, live SMTP, backup restoration, or production telemetry validation. The container image build passed locally; the modified GitHub Actions workflow has not yet run remotely. GitHub must have `Merge gate` configured as a required branch check to enforce merge protection. Static security findings are not a penetration test. No application behavior was changed by this assessment.
