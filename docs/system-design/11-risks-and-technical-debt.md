# 11. Risks and Technical Debt

This chapter is an index into open items already recorded elsewhere in this
document, not a new risk assessment. Each control-implementation document
already tracks its own gaps as "Required production decisions," "Required
deployment decisions," or per-row status such as `Unimplemented`, `Partial`,
or `Deployment decision required`. Re-deriving that assessment here would
duplicate it and risk it going stale; this chapter only surfaces where to
find it.

## Security

| Area | Open items are recorded in |
| --- | --- |
| Authentication | [Authentication's "Required production decisions"](08-crosscutting-concepts/security/authentication.md#required-production-decisions) — realm/client topology, MFA, brute-force policy, step-up/re-authentication, mTLS, password-recovery policy. |
| Sessions | [Sessions' "Required production decisions"](08-crosscutting-concepts/security/sessions.md#required-production-decisions) — timeout rationale, privilege-change revocation coverage, reauthentication for risk events, session-table protection, cookie-prefix and `Clear-Site-Data` adoption, anomaly detection. |
| HTTP headers | [Headers' "Required production decisions"](08-crosscutting-concepts/security/headers.md#required-production-decisions) — `Strict-Transport-Security` tuning, removing/normalizing the `Server` header at the edge. |
| Tomcat/CIS hardening | [Hardening's "Required production decisions"](08-crosscutting-concepts/security/hardening.md#required-production-decisions) — image/runtime hardening, TLS termination, connector limits, mTLS, centralized log collection, re-running the control implementation after upgrades, restricting the Actuator management port. |
| Logging | [Logging's "Required deployment decisions"](08-crosscutting-concepts/logging/README.md#required-deployment-decisions) — central collector, log access/retention, client-IP trust configuration, query-parameter redaction review, product-specific audit events, request-ID trust configuration. |
| ASVS and CIS control implementations generally | Individual `Deployment decision required` and `Verification required` rows throughout [ASVS](08-crosscutting-concepts/security/asvs.md) and [Hardening](08-crosscutting-concepts/security/hardening.md) are open items in their own right, at requirement granularity finer than the summaries above. |
| Authorization | [Authorization's OWASP control implementation](08-crosscutting-concepts/security/authorization.md#owasp-control-implementation) — the one genuine open item is horizontal privilege separation: an adopter that introduces multi-tenancy or per-user resource ownership must add its own object-level checks, since the current model grants any holder of a management role access to that role's entire resource collection by design. |

## Beyond security

| Item | Recorded in |
| --- | --- |
| Only Security has a complete, requirement-by-requirement control implementation | [Quality Requirements](10-quality-requirements.md) — the other ISO/IEC 25010:2023 characteristics have no equivalent assessment yet. |
| Production database product not yet chosen | [Building Block View](05-building-block-view.md#schema-ownership) — the template does not prescribe one; H2 is test-scope only. |
