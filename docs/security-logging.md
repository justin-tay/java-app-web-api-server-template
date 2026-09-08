# Security logging

This template emits structured JSON to the console in Elastic Common Schema (ECS)
format. It records request lifecycle events and security audit events without
recording request bodies, response bodies, cookies, authorization headers, or
unredacted OAuth/OIDC credentials.

This document maps the implementation to the [OWASP Logging Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).
`Implemented` means the base template does it today. `Partial` identifies a
deliberate boundary or remaining application work. `Deployment responsibility`
means the control belongs to the log platform or runtime rather than this
application.

The shared ECS schema, extensions, redaction, and correlation rules are in
[security-logging-schema.md](security-logging-schema.md). The trigger, source,
valid values, and field-by-field contract for every emitted event are in
[security-logging-event-reference.md](security-logging-event-reference.md).

## Design and ownership

* Spring Boot's ECS console formatter serializes the events as JSON.
* `SecurityLoggingContextFilter` establishes the request correlation ID and
  direct peer address in MDC, plus the user name where already authenticated.
  The peer address is not a unique correlation ID and may be a proxy rather
  than the end user. It clears MDC at request entry and exit so a reused
  servlet thread cannot associate an event with a previous request.
* `client.ip` is optional request-scoped MDC context. `WebSecurityConfiguration`
  supplies the safe `ClientIpResolver.none()` bean by default. A service can
  replace it with `TrustedHeaderClientIpResolver`,
  `XForwardedForClientIpResolver`, `CloudFrontViewerAddressClientIpResolver`,
  or a service-specific resolver with its own trusted-proxy CIDRs.
* `http.request.id` is generated as a UUID by default. A service can replace
  the `RequestIdResolver.none()` bean with `CloudFrontRequestIdResolver` or an
  ingress-specific implementation to retain an upstream correlation ID.
* `RequestLoggingFilter`, immediately after the context filter, emits
  `receive_request` and `complete_request` events. The latter includes
  outcome, response status, matched route, and duration.
* `SecurityAuditEventLogger` emits authentication, authorization, and logout
  audit events. `ProblemDetailAccessDeniedHandler` also records CSRF denials.
* `ApiResponseEntityExceptionHandler` emits a `validate_input` event for each
  rejected input-validation rule without recording the submitted value or
  validation message.
* `ApplicationLifecycleEventLogger`, registered before context creation through
  `spring.factories`, records application starting, started, failed-to-start,
  and stopped events. It deliberately does not log `ApplicationStartingEvent`,
  because the logging subsystem is not available at that point.
* The receiving log collector/SIEM owns transport, retention, access control,
  alerting, and any separation of operational, audit, and security datasets.

## Request lifecycle logging

Each request produces correlated `receive_request` and `complete_request` ECS
events. They include the scheme, host, port, path, matched route, method, status,
and duration. The request ID allows support staff to locate the pair and identify
requests that have started without completing.

Query parameter values are logged in `url.query`, except names in the redaction
list, whose values are replaced with `[REDACTED]`. All supplied names are also
recorded as `url.query_keys`. The default list protects OAuth and OpenID Connect
credentials and correlation values. It is intentionally source-controlled as
`QUERY_PARAMETER_REDACT_LIST` in `WebSecurityConfiguration`; extend it for
application-specific credentials, identity data, payment data, and free-text
search parameters.

## OWASP recommendation matrix

| OWASP area and recommendation | Status | Current treatment or rationale |
| --- | --- | --- |
| Define security logging use cases and distinguish security, operational, and audit records. | Partial | The events are categorised with ECS `event.category`, `event.type`, `event.action`, and `event.outcome`, so a collector can route them. Authorization denials use the allowed `web` and `api` categories with `access`/`denied` types. The application writes one ECS console stream; distinct retained datasets are a collector/SIEM decision. |
| Capture events from the application and other relevant layers. | Partial | Application authentication, authorization, CSRF, and request lifecycle events are captured here. Edge/WAF, reverse proxy, TLS terminator, database, and identity-provider logs are outside the process and should be collected separately. A service can select `CloudFrontRequestIdResolver` to retain CloudFront's `X-Amz-Cf-Id` as correlation metadata. |
| Treat event data from other trust zones as untrusted. | Implemented | Request-derived values are logged as structured field data, not interpolated into message templates. An upstream request ID is used only for correlation, never authorization or identity. Operators must still treat all client-provided values as untrusted during analysis. |
| Use a centralized log collection system and record to stdout where appropriate. | Partial | The application writes JSON to stdout, which is suitable for container/platform collection. Shipping to a central collector, handling collector failure, and monitoring delivery are deployment responsibilities. Local files and databases are intentionally not used by the template. |
| Use a standard, documented format. | Implemented | Spring Boot ECS JSON is enabled. Shared fields and extensions are documented in [security-logging-schema.md](security-logging-schema.md); individual event contracts are documented in [security-logging-event-reference.md](security-logging-event-reference.md). |
| Restrict access to logs. | Deployment responsibility | Console access, collector credentials, SIEM roles, and index permissions are not controllable by this application. Grant least privilege and segregate security-log readers from ordinary application users. |
| Log input validation failures. | Implemented | `ApiResponseEntityExceptionHandler` emits `WARN` events with `event.action=validate_input`, outcome, 400 status, path, validation mechanism, rule code, and (where known) field/parameter path. It emits one event per rejected Bean Validation field or constraint and deliberately omits submitted values, request bodies, exception messages, and validation messages. |
| Log output validation failures. | Not applicable to the base template | The template has no domain output-validation layer. Applications that validate or transform security-sensitive outbound data should emit a redacted structured event when that control fails, without logging the protected payload. |
| Log authentication successes and failures. | Implemented | `SecurityAuditEventLogger` records successful and failed logins with outcome, user name, and failure exception type. It deliberately omits exception messages and credentials. |
| Monitor session lifecycle without logging credentials. | Implemented for known application causes | `SessionLifecycleAuditLogger` uses a random, server-side audit identifier as `session.id` for audit-ID initialization, fixation renewal, logout, absolute timeout, and concurrent-session expiry. It never logs the session cookie or Spring Session ID. JDBC idle cleanup and invalid-ID anomaly detection require separately designed hooks and alert policy. |
| Log authorization failures. | Implemented | Access-denied events use `event.category=[web, api]`, `event.type=[access, denied]`, `event.action=authorize_access`, and `event.outcome=failure`. CSRF denials use `event.action=validate_csrf_token` and separately record path, direct peer address, exception type, and their known 403 response status. |
| Log session-management failures or suspicious session activity. | Partial | Spring Security manages the configured session/token flows, but the template does not infer suspicious session changes or log session identifiers. Add domain-specific events for session revocation, fixation detection, or account-switching requirements. |
| Log application and system errors, and start-up/shutdown conditions. | Partial | `ApplicationLifecycleEventLogger` records structured start, started, failed-to-start, and stopped events. Unexpected exceptions handled during Spring MVC dispatch emit a structured `ERROR` event with `event.action=process_request`, safe error type, response status, request path, correlation ID, and stack trace. Failures before `ApplicationContextInitializedEvent` and logging-sink health remain framework/platform responsibilities; platform monitoring should detect container restarts and missing log flow. |
| Log higher-risk business actions, data changes, privilege changes, imports/exports, and use of privileged functions. | Not applicable to the base template | There is no business domain in this template. Applications built from it must add auditable, structured events at the business-action boundary, including actor, target, action, outcome, and correlation ID. |
| Log unexpected HTTP methods, protocol/TLS failures, network failures, and other attacks. | Partial | Every request that reaches the filter has its method and lifecycle logged. Rejected traffic before the application, TLS handshake failures, malformed requests, and WAF detections belong to the load balancer, proxy, WAF, or servlet container logs. No attack-detection rules are shipped by the template. |
| Log legal, consent, fraud, business-rule, sequencing, and other optional events when relevant. | Not applicable to the base template | These require product-specific definitions and retention rules. Add them as structured domain audit events rather than trying to infer them from access logs. |
| Record the “when, where, who, and what” of an event. | Implemented | Events include timestamps, service/environment metadata, request ID, event category/type/action/outcome, method, path, server address/port, user where known, and error type for security failures. Request completion also includes status, route, start/end, and duration. See the schema reference for deliberate omissions. |
| Include source address, user agent, user identity, targets, and identifiers when useful. | Implemented, with deployment configuration | Request-scoped events include the direct peer address as `source.ip`; it is not a proxy-normalized client IP. When the default `ClientIpResolver` bean is replaced with a trusted resolver, they also include the validated `client.ip`. User identity is logged after authentication. The template intentionally omits user-agent, headers, bodies, session IDs, and target business objects. |
| Exclude or mask secrets, credentials, tokens, keys, session identifiers, sensitive personal data, and payment data. | Implemented, with an operational caveat | Bodies, cookies, authorization headers, passwords, tokens, and session IDs are not logged. Query values are recorded in `url.query` after redacting the hard-coded OAuth/OIDC names in `WebSecurityConfiguration.QUERY_PARAMETER_REDACT_LIST`; the parameter names remain in `url.query_keys`. Review new endpoints for secrets embedded in query values or path segments before release. |
| De-identify, pseudonymize, or otherwise minimize personal data where required. | Partial | `user.name` is useful for an audit trail but may be personal data. The template does not hash it because that can reduce support and audit usefulness. Configure access, retention, lawful basis, and any pseudonymization in the deployment and product privacy design. |
| Provide a sufficient default log level; do not permit essential security logging to be disabled casually. | Partial | Security lifecycle and audit events are emitted at `INFO`/`WARN`. A global logging-level change can still suppress them, so production logging configuration must be change-controlled and monitored. The redaction list is source-controlled rather than externally mutable. |
| Record and review logging configuration changes. | Deployment responsibility | This template does not offer a runtime endpoint for logging changes. Changes to source, Spring configuration, and collector configuration should be code-reviewed, deployed through change control, and audited by the delivery platform. |
| Sanitize event data to prevent log injection; use safe encoding. | Implemented | Values are passed as structured key-values to the JSON ECS formatter rather than concatenated into log messages. JSON escaping protects record structure. Query parameters are decoded and sensitive values redacted before logging. Downstream viewers must render fields safely. |
| Ensure logging failures do not stop the application. | Partial | Normal Logback console appenders fail independently of request processing, but sink failure behaviour is owned by the runtime/collector. Exercise it during operational testing and alert on dropped/blocked log delivery. |
| Synchronize clocks and record timestamps consistently. | Partial | ECS emits `@timestamp`; lifecycle events also carry `event.start`, `event.end`, and nanosecond `event.duration`, derived from the same request timestamps. Host/container NTP or cloud time synchronization is a deployment requirement. |
| Verify logging during security, functional, and performance testing. | Partial | Automated tests verify the paired request events, redaction, correlation, user propagation, route, outcome, and duration arithmetic. Add tests for product-specific audit events, hostile input, async/error/timeout paths, performance, and collector outage handling. |
| Protect log collection, transit, storage, and backups against confidentiality, integrity, and availability attacks. | Deployment responsibility | The application emits to stdout only. Use authenticated, encrypted shipping; protected storage and backups; immutable/audited access where required; retention and disposal controls; and monitoring for collector failure or unexpected cessation. |
| Monitor logs and integrate them with incident response. | Deployment responsibility | The template provides stable fields suitable for detections and correlation but provides no SIEM rules, paging, runbooks, or incident-response workflow. The owning service team must define these before production use. |
| Document logging and brief support/operations teams. | Implemented | This document, the field reference, code comments, and request correlation behaviour are versioned with the template. Service owners must extend the documentation when they add business events or change privacy/retention obligations. |

## Required deployment decisions

Before a non-local deployment, the service owner should make these decisions
explicitly:

1. Configure a central collector to ingest the ECS console stream over a
   protected path and alert when the source becomes silent.
2. Apply least-privilege access, retention, backup, and deletion policies to
   the resulting security-log dataset.
3. Decide which proxy is trusted to supply a client address. Only then add a
   normalized client/source IP field; never blindly trust forwarding headers.
4. Review `QUERY_PARAMETER_REDACT_LIST` whenever an endpoint accepts a secret
   in the URL. Prefer credentials in protected request bodies/headers, not URLs.
5. Add product-specific audit events for privileged actions and sensitive data
   access, and test them as part of the security test plan.
6. If an end-user client IP is needed, replace the `ClientIpResolver` bean in
   `WebSecurityConfiguration` with a resolver whose trusted-proxy CIDRs match
   the deployment. Choose `TrustedHeaderClientIpResolver` for a normalized
   header, `XForwardedForClientIpResolver` for a forwarded chain, or
   `CloudFrontViewerAddressClientIpResolver` for CloudFront's address-and-port
   header. Do not use a resolver unless the ingress trust boundary is explicit.
7. If an upstream request correlation ID is needed, replace the
   `RequestIdResolver` bean in `WebSecurityConfiguration` with the matching
   ingress implementation, such as `CloudFrontRequestIdResolver`. Otherwise,
   retain the generated UUID.
