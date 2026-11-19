# Slotix change review

What this change does: The backend now measures peak capacity, returns unique ordered slots and enforces exact calendar limits with customer filtering in SQL. The browser invalidates old availability after search edits, sends current notes, freezes busy forms and rejects impossible dates. The implementation reuses existing clocks, transactions, forms and date helpers without adding dependencies.

## Review performed

Reviewed the complete diffs and the connected availability, creation, rescheduling, calendar, editor and date-helper paths. Searched callers after changing the calendar repository signature and removing the old occupancy count query. Creation still acquires the company write lock before calculating availability, rescheduling still excludes its own booking, and capacity-one database protection remains in place.

The capacity sweep groups start and end changes at the same instant. This preserves half-open interval semantics and sums persisted units. The ordered result map removes duplicate start instants; duration is fixed for each request. Customer ownership is bound as a query parameter, and only the existing staff authorization branch passes a null ownership filter.

Browser edits invalidate only the fields that determine availability. Notes and customer assignment remain editable after a search and update the submitted request. Busy native controls keep their reactive form values; existing submit guards still apply. Calendar end-date arithmetic now validates before normalizing.

## Validation

| Repository | Command | Result |
| --- | --- | --- |
| Backend | `./mvnw -q verify` | Passed after the functional fixes |
| Backend | `./mvnw -B -Pquality clean verify` | Passed; 124 tests, zero failures/errors/skips, isolated PostgreSQL Testcontainers |
| Frontend | `npm run verify` | Passed; typecheck, ESLint, Prettier, 34 unit tests, coverage generation, production build and 16 Playwright tests |
| Both | `git diff --check` | Passed |

Five backend regression tests were added. Four new frontend tests plus a stronger busy-state assertion cover the corrected behavior. Before fixes, the initial focused runs recorded three backend failures and four frontend failures. Existing browser tests passed on desktop Chromium and mobile emulation with mocked API responses. No development records were changed by these checks.

Backend line coverage is 93.43% (2389/2557). Frontend line coverage is 27.99%; the passing focused tests do not establish broad frontend unit coverage.

The quality profile generates reports rather than enforcing zero warnings: SpotBugs/FindSecBugs emitted 154 candidates, PMD 140 rule violations and CPD zero duplicate blocks. These totals are not confirmed vulnerabilities. Changed production paths were reviewed, including framework security context assumptions and parameterized repository queries. A full classification of every static-analysis candidate was not part of this change; no claim of a warning-free repository is made. The new capacity sweep has a PMD null-assignment warning for resetting interval state; replacing it with an abstraction would not improve behavior.

No additional actionable defect was found in the final changes. Existing unrelated local files were excluded: backend `.DS_Store` files and frontend `angular.json`.

Verdict: Ship this change. This is a review of the fixes, not production certification.

Not checked: Live browser/API/database flows, deployed infrastructure, sustained multi-instance throughput, every static-analysis candidate and fresh external dependency advisories. Company write locking still serializes writes within a tenant, and large staff calendars have not been load-tested.
