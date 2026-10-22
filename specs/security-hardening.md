# Security hardening

## Input boundaries

Company slugs use possessive repetition, accept only lowercase alphanumeric segments separated by single hyphens, and are limited to 80 characters. Legal names, display names, and contact email addresses have explicit limits. Booking notes, cancellation reasons, and block reasons are limited to 2,000 characters at the HTTP boundary and for new database writes. Historical oversized rows are preserved by unvalidated PostgreSQL checks; operators must review and remediate them before validating the constraints.

`RequestValidationSecurityTest` submits the previously failing 10,001-character slug and a larger malformed slug under a bounded timeout. Integration tests use PostgreSQL Testcontainers.
