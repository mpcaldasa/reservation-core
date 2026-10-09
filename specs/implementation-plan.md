# Implementation progress

This plan tracks the repository's current implementation and remaining work. It is a development sequence, not a promise that every possible product feature is required.

| Track | Current status | Remaining work |
| --- | --- | --- |
| Company and identity administration | Company membership, expiring invitations, password recovery, global account lifecycle, and platform administrator role management implemented | None in the current backend scope |
| Permissions | Current roles, identity state, and private resource restrictions implemented | Any future anonymous public catalog |
| Booking operations | Creation, approval, rejection, cancellation, rescheduling, check-in, completion, no-show, pending expiration, and idempotency key expiry implemented; integration coverage verifies transition boundaries, rescheduling success and failure paths, cancellation notice boundaries, expiration cutoff, concurrent expiration passes, and key cleanup | None in the current backend scope |
| Resource administration | Creation, editing, activation, deactivation, soft deletion, schedule and block management, policy lifecycle, safe assignment closure/replacement/removal, and integration coverage for lifecycle guards and overlapping concurrent writes implemented | None in the current backend scope |
| Operational tools | Audit persistence and filtered queries, failed-delivery inspection and replay, structured correlated logs, HTTP/JDBC and scheduled-job traces, bounded business metrics, CloudWatch/ADOT and Datadog OTLP configuration, ECS/ALB and exhausted-notification CloudWatch alarms, operations dashboard, optional SNS email routing, SMTP delivery, retries, health endpoints, and OpenAPI implemented | Live SMTP verification and production telemetry validation |
| Engineering and API consistency | English specifications, a stable JSON API error envelope, all 79 `ApiErrorCode` values documented, and integration coverage for current booking, resource, policy, company, identity, and pagination error paths are implemented | Preserve the error and pagination contracts as new endpoints are added |
| Deployment | Containers, CI integration verification, Compose configuration checks, Terraform formatting and validation, and validated AWS infrastructure implemented | Image publishing, staging deployment automation, production operational checks, and backup restoration exercises |

A frontend and any commercial billing features require a separate product scope. The current repository is the backend reservation core.
