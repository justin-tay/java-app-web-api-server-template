<!-- arc42-generated -->
# OWASP Logging Cheat Sheet

The OWASP Logging Cheat Sheet control implementation for this base template
maps against the [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).
It is a planning and verification aid, not a conformance claim.

Its scope is what the application itself logs and how. Collector,
transport, storage, retention, and monitoring/alerting infrastructure are
platform concerns the template cannot decide; rows that land there are
marked as a deployment responsibility rather than left unassessed.

## Scope and status meanings

The Cheat Sheet is a set of practices, not a numbered requirement list like
ASVS or a benchmark. This page follows the Cheat Sheet's own headings in
its own order, "Introduction" and "Purpose" excepted, since those are
motivational rather than actionable, the same way this template's other
control implementations skip a standard's own preface.

| Status | Meaning |
| --- | --- |
| Implemented | The template does this today; evidence is cited in the row. |
| Implemented for known causes | True for every cause the application itself can detect. A cause outside the application's visibility, such as a database-driven session expiry, is not covered. |
| Partial | A deliberate boundary: part of the recommendation is met, the remainder is out of scope for a template with no business domain, or is explicitly left to the adopter. |
| Not applicable | The base template has no capability the recommendation addresses (file uploads, payment data, a business domain). Reassess before adding one. |
| Deployment responsibility | The recommendation belongs to the log collector, SIEM, runtime, or an operational process, not application code. |

For the template's related evidence, see [Logging and Monitoring](README.md),
[Logging schema](schema.md), [Logging event reference](event-reference.md),
and, for the overlapping ASVS V16 mapping, [ASVS](../02-security-and-authentication/asvs.md#v16-security-logging-and-error-handling).

## Event Data Sources

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Capture event data primarily from application code; treat data from other trust zones as untrusted. | Implemented | Application code, filters, exception handlers, and `SecurityAuditEventLogger`/`SessionLifecycleAuditLogger`, is the template's only event source; it has no client-side instrumentation, embedded device, or network appliance also feeding events in. Request-derived values are always logged as structured fields, never trusted or interpolated as safe content; see "Sanitize event data..." under Event Collection below. |

## Where to Record Event Data

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Use a dedicated, access-restricted filesystem partition, database account, or secure protocol for log storage; keep logs out of web-accessible locations. | Not applicable | The application writes only to stdout; it owns no filesystem partition, database table, or web-accessible directory for these controls to apply to. Where the resulting stream is collected and stored is a deployment decision; see Network Architecture and Protection below. |

## Which Events to Log

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Log input validation failures, including invalid encodings and protocol violations. | Implemented | `ApiResponseEntityExceptionHandler` emits a `validate_input` event per rejected Bean Validation field or constraint; see the "Input validation failed" entry in [event reference](event-reference.md). |
| Log output validation failures. | Not applicable | The template has no output-validation layer of its own; an adopter that validates outbound data should add a matching event without logging the protected payload. |
| Log authentication successes and failures. | Implemented | `SecurityAuditEventLogger` records `login` with outcome, user name, and (on failure) exception type; see the "Authentication succeeded"/"Authentication failed" entries in [event reference](event-reference.md). |
| Log authorization and access-control failures. | Implemented | `SecurityAuditEventLogger` records `authorize_access` denials, and `ProblemDetailAccessDeniedHandler` records CSRF denials the same way; see the "Authorization denied" entry in [event reference](event-reference.md). |
| Log session-management failures. | Implemented for known causes | `SessionLifecycleAuditLogger` records session creation, renewal, privilege change, and destruction under an application-local audit identifier (never the session cookie), and a `resume_session` failure for a request carrying a session ID that is not found, which is how idle expiry is logged, on the session's next use. Causes the application cannot detect are not inferred: an unrecognized ID is not called expired or forged, and JDBC cleanup of a session never used again produces no event. See [README](README.md#security-relevant-events), [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md), and [ADR 0017](../../../adr/0017-invalid-session-and-privilege-change-logging.md). |
| Log application errors and system events. | Implemented | `ApiResponseEntityExceptionHandler`'s last-resort handler and `ProblemDetailErrorController` both log unexpected exceptions at `ERROR`; see the "Request processing failed" entries in [event reference](event-reference.md). |
| Log application start-up, shutdown, and initialization events. | Implemented | `ApplicationLifecycleEventLogger` records starting, started, failed-to-start, and stopped events, registered ahead of context creation through `spring.factories`. It deliberately skips the earliest starting event, since the logging subsystem is not yet available to emit it. |
| Log use of higher-risk functionality, default/shared accounts, and access to sensitive data. | Partial | The template's higher-risk functionality is the administration API (`UserAdminController`, `GroupAdminController`, `RoleAdminController`, backed by `AdministrationService`), which creates, updates, and deletes users, groups, and roles. No dedicated audit event records these administrative changes. They are visible only through the generic `receive_request`/`complete_request` request events from `RequestLoggingFilter` (method, route, status, user), and indirectly through `destroy_session` events when `AdministrationService.updateUser()` revokes a user's sessions with reason `privilege_change` or `AdministrationService.deleteUser()` revokes them with reason `account_deleted`, which occur only if that user has active sessions. The base template has no default/shared account or sensitive-data feature; an adopter must add these events at their own privileged-action or sensitive-data boundary. |
| Log network connection failures, including TLS and certificate validation. | Deployment responsibility | TLS termination happens at the servlet container or a fronting load balancer/proxy, outside the application's log surface; see [Hardening](../02-security-and-authentication/hardening.md). |
| Log legal opt-ins, consent, and suspicious business-logic activity. | Not applicable | The base template has no consent flow or business logic to flag as suspicious; an adopter must define these for their own domain. |

## Event Attributes

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Record the date and time of the event, in a consistent, unambiguous format. | Implemented | ECS emits `@timestamp` in UTC by construction; see [Logging schema](schema.md). |
| Record an identifier that lets related events be linked together. | Implemented | `http.request.id` correlates every event produced during one request; `trace.id`/`span.id` additionally correlate across this application's own log lines and, for a caller that propagates W3C trace context, with that caller's; see [README](README.md#correlation-ahead-of-the-security-chain) and [ADR 0011](../../../adr/0011-trace-correlated-structured-logging.md). |
| Record source address and, where authenticated, user identity. | Implemented | `source.ip` is always the direct peer address. `client.ip` is only populated once a deployment wires up a trusted `ClientIpResolver` for its actual proxy chain; the default resolver trusts nothing. `user.name` is added once authentication resolves. |
| Record the entry point (URL, HTTP method) and result status. | Implemented | `receive_request`/`complete_request` carry `url.path`, `http.request.method`, `http.response.status_code`, and `event.outcome`; see [Logging schema](schema.md). |
| Classify the event (category, type, action, severity) so it can be queried and routed. | Implemented | Every event carries a fixed ECS `event.category`/`event.type`/`event.action`, documented per event in the [event reference](event-reference.md); severity is the SLF4J log level the event is emitted at. |
| Capture extended detail (stack trace, system error) for unexpected failures. | Implemented | An unexpected exception is attached with SLF4J's `setCause()`, letting the ECS formatter derive `error.type`/`error.message`/`error.stack_trace` automatically; an expected, client-driven failure instead gets a hand-added `error.type` with no message or trace, since the exception message could echo rejected request content. See [README](README.md#what-never-reaches-a-log-line). |

## Data to Exclude

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Never log data unless there is a lawful basis and business need to do so. | Implemented | The event catalogue in [Logging schema](schema.md#event-index) is a closed, reviewed list; new code extends it rather than logging ad hoc messages. |
| Exclude request/response bodies, headers, and cookies. | Implemented | None of these are ever fields any logging code in the template has access to write; see [README](README.md#what-never-reaches-a-log-line). |
| Exclude or mask session identifiers. | Implemented | Session-lifecycle events key on a random, application-local `session.id`, deliberately never the session cookie or the raw Spring Session ID; see [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md). |
| Exclude access tokens, passwords, encryption keys, and other credentials. | Implemented | Passwords, tokens, and keys are never logged; a fixed list of OAuth/OIDC query-parameter names (`access_token`, `client_secret`, `refresh_token`, `code`, and others in `WebSecurityConfiguration.QUERY_PARAMETER_REDACT_LIST`) is redacted from `url.query` before it is logged, while the parameter names themselves remain in `url.query_keys`. |
| Remove or mask other sensitive personal data. | Partial | `user.name` is logged unmasked, since hashing it would reduce audit and support usefulness; whether that value counts as personal data, and what retention/access controls it then needs, is a deployment and product privacy decision. |
| Exclude data that exceeds the log sink's classification level, or that is illegal to collect in the deployment's jurisdiction. | Deployment responsibility | The template has no awareness of the deployment's data-classification policy or jurisdiction; the service owner must review the event catalogue against both before production use. |

## Customizable Logging

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Provide a sufficient default log level without permitting security logging to be disabled casually. | Partial | Security lifecycle and audit events are emitted at `INFO`/`WARN`, Spring Boot's defaults; nothing in the template stops a global logging-level change from suppressing them, so production logging configuration must be change-controlled. The redaction list itself is source-controlled, not externally mutable. |
| Record and review logging-configuration changes. | Deployment responsibility | The template exposes no runtime endpoint to change logging configuration; changes to source, Spring configuration, or collector configuration go through ordinary code review and deployment change control. |

## Event Collection

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Use a framework-supported logging mechanism rather than a bespoke one. | Implemented | Spring Boot's native structured-logging support (`logging.structured.format.console: ecs` in `application.yaml`) formats events directly to ECS JSON; the template adds no separate logging library dependency. |
| Sanitize event data to prevent log injection; encode correctly for the log format. | Implemented | Values are passed as structured key-value fields to the ECS JSON formatter rather than concatenated into a message string, so request-derived data cannot alter log-record structure; see [README](README.md#what-never-reaches-a-log-line). |
| Ensure a logging failure does not stop the application from operating. | Partial | Normal console-appender failure is independent of request processing by construction, but sink-failure behavior beyond that (a blocked or full stdout pipe) is owned by the runtime and collector, not exercised by the template's own tests. |
| Synchronize time across servers and log events consistently. | Partial | ECS emits `@timestamp`, and request-lifecycle events additionally carry `event.start`/`event.end`/`event.duration` derived from the same clock; synchronizing that clock across hosts or containers (NTP or the platform's time service) is a deployment requirement. |

## Verification

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Cover logging behavior with automated tests: correlation, redaction, and event contracts. | Implemented | Automated tests verify paired request events, query-parameter redaction, user propagation, route, outcome, and duration arithmetic (for example `RequestLoggingFilterTest`, `ApiResponseEntityExceptionHandlerTest`). |
| Test logging under hostile input, error paths, and during security/penetration testing. | Partial | Existing tests cover rejection paths this template ships (invalid input, CSRF, firewall rejection); an adopter's own hostile-input and penetration testing should extend coverage to any business logic added on top. |
| Verify logging does not become a denial-of-service vector. | Not applicable | The base template has no unbounded or user-controlled logging volume; `AdminPageable`'s page-size cap (see [ASVS V15.1.3](../02-security-and-authentication/asvs.md)) already bounds the one resource-intensive read the template has. |

## Network Architecture

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Use a centralized log-collection system, writing to a standard, documented format. | Partial | The application writes ECS JSON to stdout, suitable for container/platform collection; shipping it to a central collector, and detecting when that collection stops, is a deployment responsibility. See [README](README.md#why-ecs). |
| Separate log storage and retrieval from the business application servers themselves. | Deployment responsibility | The template has no log storage of its own; it only ever writes to stdout, so this is entirely the collector/platform's architecture to decide. |

## Release

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Include logging mechanism details in release documentation and brief the application owner. | Implemented | This page, [Logging schema](schema.md), and [Logging event reference](event-reference.md) are the release-facing documentation of what is logged, how, and where responsibility shifts to the deployment. |

## Operation

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Detect when logging has stopped, or when logs have been tampered with or accessed without authorization. | Deployment responsibility | Detecting logging cessation and unauthorized log access happens in the collector/SIEM, outside the application's own process; the application has no visibility into its own log stream once written to stdout. |

## Protection

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Build in tamper detection; restrict and periodically review who can read log data; use secure transmission for untrusted networks. | Deployment responsibility | Collector access control, storage permissions, transmission security, and tamper protection are entirely platform concerns the application cannot configure; see [README](README.md#why-ecs). |

## Monitoring of Events

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Incorporate application logging into log management, alert on serious events, and share event information with detection systems. | Deployment responsibility | The template produces stable, queryable fields (`event.category`/`event.type`/`event.action`/`event.outcome`) suitable as detection inputs, but ships no alerting rule, paging integration, or SIEM correlation of its own; see [README](README.md#consuming-the-logs). |

## Disposal of Logs

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Retain logs for the required period, then dispose of them per legal, regulatory, or contractual obligations. | Deployment responsibility | The template defines no retention or disposal policy; this is a collector/SIEM and organizational-policy decision. |

## Confidentiality

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Prevent unauthorized access to logs and exfiltration of secrets or PII through them. | Implemented | The application-side control is not logging secrets or PII in the first place (see Data to Exclude above); who can read the resulting stdout stream once collected is a deployment access-control decision. |

## Integrity

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Control authorization to modify logs; prevent payload injection through them. | Implemented | Structured, key-value logging prevents an attacker from injecting a forged log record through request data (see Event Collection above); protecting the collected log stream itself from modification is a deployment concern. |

## Availability

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Prevent disk exhaustion or flooding from degrading the application; maintain adequate logging performance. | Deployment responsibility | The application writes to stdout, not a local disk it manages; container/platform log-volume limits and rotation are the deployment's responsibility. Logging calls themselves add no unbounded work per request. |

## Accountability

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Prevent an attacker from suppressing or disabling security logging to cover their tracks. | Partial | The template offers no runtime control that disables logging, and the redaction list is source-controlled rather than externally mutable, but a global logging-level change made outside the application (see Customizable Logging above) could still suppress `INFO`/`WARN` security events; production configuration must be change-controlled to close this gap operationally. |

<!-- arc42-manual: Record the deployment platform's actual log-retention period, access-control model, and centralized collector once a production log pipeline is chosen. -->
<!-- /arc42-generated -->
