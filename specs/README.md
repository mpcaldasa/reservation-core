# Slotix specifications

Specifications describe observable behavior, module boundaries, and the verification required to change them. All new specifications, code identifiers, comments, and engineering documentation are written in English.

- [Engineering standards](engineering-standards.md): implementation and review conventions.
- [Quality assessment](quality-assessment.md): coverage baseline, static analysis, and reviewed maintenance priorities.
- [Implementation progress](implementation-plan.md): current work and remaining development tracks.
- [API error contract](api-errors.md): response envelope, error codes, and client handling rules.
- [Company onboarding and memberships](company-onboarding-and-memberships.md): initial platform access, company administration, and current authorization.
- [Resource visibility](resource-visibility.md): catalog filtering, private resource access, and booking permissions.
- [Resource administration](resource-administration.md): updating, activating, and deactivating company resources.
- [Booking policy administration](policy-administration.md): policy editing, activation, deactivation, and assignment safety.
- [Booking attendance lifecycle](booking-lifecycle.md): staff check-in, completion, capacity release, and concurrent transitions.
- [Notification delivery](notification-delivery.md): delivery state, ownership, retry rules, configuration, and acceptance coverage.
- [Observability](observability.md): structured logs, metrics, traces, supported backends, local setup, and cost controls.

Update the relevant specification together with a behavior change. Document limitations explicitly; a specification must distinguish implemented behavior from future work.
