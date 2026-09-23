<!-- arc42-generated -->
# Logging and Monitoring

Every log line the application writes is structured JSON, emitted to
standard output for platform collection rather than to a file or a Tomcat
access log. There is no separate logging library dependency: Spring Boot's
native structured-logging support is configured through
`logging.structured.ecs.service` in `application.yaml`, targeting the
Elastic Common Schema (ECS) format directly. The schema, the full event
catalogue, and the redaction rules are maintained once, in
[Logging schema](schema.md) and the
[Logging event reference](event-reference.md); this page explains why the
logging is shaped the way it is and how it fits the rest of the request
pipeline.

## Why ECS

A documented, versioned schema makes every log line machine-readable and
searchable without parsing free-form messages, and lets a downstream
collector correlate fields consistently across instances
([ADR 0010](../../../adr/0010-ecs-structured-logging.md)). The event index in
[Logging schema](schema.md#event-index) is the complete catalogue: request
lifecycle, input validation and firewall rejections, authentication and
authorization outcomes, session lifecycle, unexpected processing failures,
and application startup/shutdown, each with a fixed `event.category`,
`event.type`, and `event.action`. New code that needs to record an outcome
should extend that catalogue with an ECS-aligned action rather than writing
an unstructured message.

## Correlation ahead of the security chain

Two correlation mechanisms are layered on every request, and both are
established deliberately before Spring Security's own filter chain runs,
not after:

* `http.request.id`, generated as a UUID unless a configured
  `RequestIdResolver` supplies an upstream one, along with `source.ip` and
  (when a trusted `ClientIpResolver` is wired up) `client.ip`, are all set
  in MDC by `RequestCorrelationContextFilter`.
* `trace.id`/`span.id` are OpenTelemetry identifiers, populated by
  Micrometer Tracing's own observation filter (`micrometer-tracing-bridge-otel`)
  as `traceId`/`spanId` MDC entries and renamed to their ECS names by
  `TraceCorrelationJsonMembersCustomizer` at JSON-serialization time, not by
  any filter this application owns.

Both `RequestCorrelationContextFilter` and Micrometer Tracing's filter are
registered directly with the servlet container at an order below Spring
Security's, specifically so that a request the HTTP firewall rejects before
Spring Security's internal chain or `DispatcherServlet` ever runs (the
`reject_request` event) still carries a correlation ID, a trace ID, and a
peer address, even though it has no `receive_request`/`complete_request`
pair and no `user.name`
([ADR 0012](../../../adr/0012-request-correlation-ahead-of-security-chain.md)).
Tracing has no exporter configured anywhere in the template
(`management.tracing.sampling.probability: 1.0` with no OTLP or similar
exporter, see
[Operational Concepts](../05-operational-concepts/README.md#management-and-health-surface));
`trace.id`/`span.id` exist today purely to correlate this application's own
log lines with each other and, for a caller that propagates W3C trace
context, with that caller's own logs or traces
([ADR 0011](../../../adr/0011-trace-correlated-structured-logging.md)). A
deployment that adds a span exporter should revisit the sampling probability
against the resulting export volume.

## What never reaches a log line

The sensitive-data policy in [Logging schema](schema.md#sensitive-data-policy)
is enforced structurally, not by convention: request/response bodies,
headers, cookies, session IDs, passwords, keys, tokens, and client secrets
are never fields any logging code has access to write, and a fixed list of
query-parameter names (`access_token`, `client_secret`, `refresh_token`,
`state`, and others in `WebSecurityConfiguration.QUERY_PARAMETER_REDACT_LIST`)
is redacted out of `url.query` before it is ever logged; `url.full` and
`url.original` are omitted entirely rather than reviewed case by case. The
same discipline extends to exception content. Code reporting an unexpected
bug attaches the exception with SLF4J's `setCause(throwable)` and lets the
ECS formatter derive `error.type`/`error.message`/`error.stack_trace`
automatically. Code reporting an expected, client-driven failure (input
validation, CSRF, access denied, firewall rejection, authentication
failure) deliberately does not: the exception's own message could echo
rejected request content, so only a safe `error.type` classification is
added by hand, with no message and no stack trace. A third, narrower case,
the last-resort `process_request` event in `ProblemDetailErrorController`,
needs a stack trace's class names and frames for operator debugging but
still cannot vouch for the exception's message; the
`MessageRedactedStackTraces` helper renders a `Throwable.printStackTrace()`-shaped
trace with every message, cause message, and suppressed-exception message
blanked out, so the frames stay useful without ever risking unsanitized
request content in a log sink. Mixing `setCause()` with a hand-added
`error.type` on the same event is a documented mistake to avoid, not merely
a style preference: the ECS formatter throws when the same field is written
twice, which silently drops the whole log line.

## Security-relevant events

Authentication, authorization, session lifecycle, and request-validation
outcomes are logged through the same ECS mechanism as everything else, not
a separate audit subsystem, which is what lets a request be traced through
its full lifecycle with one correlation ID regardless of which layer
produced which event:

* `SecurityAuditEventLogger` turns Spring Security's own application
  events (`InteractiveAuthenticationSuccessEvent`,
  `AbstractAuthenticationFailureEvent`, `AuthorizationDeniedEvent`,
  `LogoutSuccessEvent`) into the `login`, `authorize_access`, and `logout`
  events, without ever logging a credential or token (see
  [Authentication](../02-security-and-authentication/authentication.md#owasp-control-implementation)
  and [Authorization](../02-security-and-authentication/authorization.md#owasp-control-implementation)).
* `SessionLifecycleAuditLogger`, together with
  `SessionLifecycleAuditInitializationFilter`,
  `AbsoluteSessionTimeoutFilter`, `SessionLifecycleLogoutHandler`, and
  `LocalAuthorityRefreshFilter`, emits `create_session`, `renew_session`,
  `update_session`, and `destroy_session` events keyed by a random,
  application-local `session.id` that is deliberately never the session
  cookie or the raw Spring Session ID (see
  [Sessions](../02-security-and-authentication/sessions.md#owasp-control-implementation)
  and [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md)).
  `AuditingInvalidSessionStrategy` adds a `resume_session` failure, with no
  `session.id`, for a request carrying a session ID that is not found, which
  is where idle expiry is detected on the session's next use. These events
  are limited to what the application can state accurately: an unrecognized
  ID is not called expired or forged, and JDBC cleanup of a session never
  used again is not logged (see
  [ADR 0017](../../../adr/0017-invalid-session-and-privilege-change-logging.md)).
* `ApiResponseEntityExceptionHandler` and
  `ProblemDetailRequestRejectedHandler` emit `validate_input` and
  `reject_request` for rejected input and firewall rejections respectively,
  each correlated to the RFC 9457 response returned to the client (see
  [Error responses](../02-security-and-authentication/error-responses.md)).

## Control implementation mapping

A broader, standards-based control implementation sits alongside the
request-handling detail above rather than repeating it: [OWASP Logging
Cheat Sheet](logging.md) maps this page, [schema.md](schema.md), and
[event-reference.md](event-reference.md) against the Cheat Sheet's own
guidance on which events to log, what attributes they carry, what to
exclude, and how logs must be collected and protected once they leave the
application. The overlapping ASVS "Security Logging and Error Handling"
requirements stay in
[ASVS V16](../02-security-and-authentication/asvs.md#v16-security-logging-and-error-handling)
rather than being duplicated here, since `asvs.md` is the template's one
complete, 345-requirement ASVS mapping.

## Consuming the logs

Use `http.request.id` to join a request's lifecycle and in-request audit
events together; use `trace.id` only to correlate across this application's
own logs or with an external caller's propagated trace, since nothing is
exported today. Query on `event.category`, `event.action`, and
`event.outcome` rather than matching on `message` text, which exists for a
human reader, not a query. `url.path`, `url.query`, `user.name`, and
`source.ip` are operational data that can identify a person or a request
pattern; apply the same access and retention controls to the log sink that
would apply to the data itself.

<!-- arc42-manual: Record the deployment platform's actual log collector, retention period, and access controls once a production log pipeline is chosen; the template only guarantees what is emitted to standard output, not where it ends up or for how long. -->
<!-- /arc42-generated -->
