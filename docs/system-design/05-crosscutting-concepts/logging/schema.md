# Logging schema

The application writes JSON using Spring Boot's ECS formatter. This document
defines the shared schema, field sets, extensions, and safety rules. The
[event reference](event-reference.md) is the authoritative
per-event contract: its entries state when each event is generated and every
field that it emits.

ECS groups related fields into field sets; the [Base field set](https://www.elastic.co/docs/reference/ecs/ecs-base)
is the only set defined directly at the event root. This template uses ECS
fields where available and documents two intentional project extensions.

## Event index

| Event | Level | `event.category` | `event.type` | `event.action` | Detailed contract |
| --- | --- | --- | --- | --- | --- |
| Request received | INFO | `web` | `access`, `start` | `receive_request` | [Event reference](event-reference.md#request-received-receive_request) |
| Request completed | INFO | `web` | `access`, `end` | `complete_request` | [Event reference](event-reference.md#request-completed-complete_request) |
| Input validation failure | WARN | `web` | `error` | `validate_input` | [Event reference](event-reference.md#input-validation-failed-validate_input) |
| Firewall rejection | WARN | `web` | `error` | `reject_request` | [Event reference](event-reference.md#request-rejected-by-the-http-firewall-reject_request) |
| Authentication success | INFO | `authentication` | `info` | `login` | [Event reference](event-reference.md#authentication-succeeded-login) |
| Authentication failure | WARN | `authentication` | `denied` | `login` | [Event reference](event-reference.md#authentication-failed-login) |
| Authorization denial | WARN | `web`, `api` | `access`, `denied` | `authorize_access` | [Event reference](event-reference.md#authorization-denied-authorize_access) |
| CSRF denial | WARN | `web`, `api` | `access`, `denied` | `validate_csrf_token` | [Event reference](event-reference.md#csrf-denied-validate_csrf_token) |
| Logout success | INFO | `authentication` | `info` | `logout` | [Event reference](event-reference.md#logout-succeeded-logout) |
| Session audit ID initialized | INFO | `authentication` | `start` | `create_session` | [Event reference](event-reference.md#session-audit-id-initialized-create_session) |
| Session ID renewed | INFO | `authentication` | `info` | `renew_session` | [Event reference](event-reference.md#session-id-renewed-renew_session) |
| Session destroyed | INFO | `authentication` | `end` | `destroy_session` | [Event reference](event-reference.md#session-destroyed-destroy_session) |
| Unexpected request failure | ERROR | `web` | `error` | `process_request` | [Event reference](event-reference.md#request-processing-failed-process_request) |
| Application starting | INFO | `process` | `start` | `start_application` | [Event reference](event-reference.md#application-starting-start_application) |
| Application started | INFO | `process` | `start` | `start_application` | [Event reference](event-reference.md#application-started-start_application) |
| Application startup failed | ERROR | `process` | `start` | `start_application` | [Event reference](event-reference.md#application-startup-failed-start_application) |
| Application stopped | INFO | `process` | `end` | `stop_application` | [Event reference](event-reference.md#application-stopped-stop_application) |

## Shared Base and runtime field sets

| Field | ECS type | Presence | Meaning and source |
| --- | --- | --- | --- |
| `@timestamp` | `date` | Every event | Formatter timestamp for when the log event was emitted. |
| `ecs.version` | `keyword` | Every event | ECS version emitted by the formatter. |
| `message` | `match_only_text` | Every event | Human-readable message; query structured fields instead. |
| `log.level` | `keyword` | Every event | Logger severity. Security rejections are normally `WARN`; application faults are `ERROR`. |
| `log.logger` | `keyword` | Every event | Java logger name. |
| `process.pid` | `long` | Runtime supplied | JVM process identifier. |
| `process.thread.name` | `keyword` | Runtime supplied | Thread that emitted the event. |
| `service.name` | `keyword` | Runtime supplied | Spring application name. |
| `service.environment` | `keyword` | Runtime supplied | Deployment environment; production by default and profile-specific in local/test. |

## Shared ECS fields

| Field | ECS type | Meaning |
| --- | --- | --- |
| `event.category` | `keyword[]` | High-level event family. |
| `event.type` | `keyword[]` | Lifecycle/subcategory classification. |
| `event.action` | `keyword` | Stable lower-snake-case operation identifier. Do not encode outcome in this field. |
| `event.outcome` | `keyword` | `success`, `failure`, or `unknown` where applicable. |
| `event.start`, `event.end` | `date` | Request lifecycle boundaries. |
| `event.duration` | `long` | Request duration in nanoseconds. |
| `http.request.id` | `keyword` | Correlation ID established by `RequestCorrelationContextFilter`; generated as a UUID unless the configured `RequestIdResolver` supplies an upstream ID. It is not authentication material. |
| `trace.id` | `keyword` | OpenTelemetry trace identifier, renamed from Micrometer Tracing's `traceId` MDC entry by `TraceCorrelationJsonMembersCustomizer`. Present whenever the log statement has an active sampled span; see [ADR 0009](../../../adr/0009-trace-correlated-structured-logging.md). |
| `span.id` | `keyword` | OpenTelemetry identifier for the active server span, renamed alongside `trace.id`. |
| `http.request.method` | `keyword` | Incoming servlet request method. |
| `http.response.status_code` | `long` | Final or handler-known HTTP response status. |
| `url.scheme`, `url.path`, `url.query` | `keyword`, `wildcard`, `keyword` | Request URL components; query values are redacted before emission. |
| `server.address`, `server.port` | `keyword`, `long` | Servlet destination as observed by the application, not necessarily the public host. |
| `user.name` | `keyword` | Authenticated actor when known. |
| `user.target.name` | `keyword` | Target account of a failed authentication. |
| `source.ip` | `ip` | Direct peer address from request-scoped MDC, established by `RequestCorrelationContextFilter`. It is not a unique correlation identifier or proxy-normalized client identity. |
| `client.ip` | `ip` | Validated end-user client address from request-scoped MDC, established by `RequestCorrelationContextFilter`. Present only when the default `ClientIpResolver` bean is replaced with a trusted resolver. |
| `error.type`, `error.stack_trace` | `keyword`, `wildcard` | Safe exception classification and protected operator stack trace. |

## Project extension fields

| Field | Type | Meaning |
| --- | --- | --- |
| `http.route` | `keyword` | Best-matching Spring MVC route, or `UNKNOWN` when unavailable. Useful for aggregation without raw identifier-bearing paths. |
| `url.query_keys` | `keyword[]` | Query parameter names, including names whose values were redacted. Parameter names must not contain sensitive data. |
| `validation.field` | `keyword` | Rejected request field or parameter path. It must never contain the rejected value. |

## Correlation and lifecycle semantics

One request normally produces a `receive_request` and `complete_request` pair
sharing `http.request.id` and `event.start`. The completed event's
`event.duration` is `event.end - event.start` in nanoseconds. The filters retain
the correlation ID and authenticated user for asynchronous completion.

`http.request.id`, `source.ip`, `client.ip`, `trace.id`, and `span.id` are all
established ahead of Spring Security's filter chain — `RequestCorrelationContextFilter`
and Micrometer Tracing's observation filter are both registered directly with the
servlet container at an order below Spring Security's own (see [ADR 0010](../../../adr/0010-request-correlation-ahead-of-security-chain.md)) — so
all five are present even on a request the HTTP firewall rejects (`reject_request`),
before Spring Security's own internal filter chain, including
`AuthenticatedUserLoggingContextFilter`, is ever invoked. `user.name` remains the one field
conditional on that later chain having run: a firewall rejection is the one case
with no `receive_request`/`complete_request` pair and no `user.name`, since
authentication never occurs for a request that never reaches `DispatcherServlet`.

`trace.id`/`span.id` are a second, OpenTelemetry-native correlation pair layered
alongside `http.request.id`. Unlike `http.request.id`, they are not added by a
filter in this application; Micrometer Tracing's own observation filter
populates `traceId`/`spanId` MDC entries directly, and
`TraceCorrelationJsonMembersCustomizer` renames them to ECS field names at JSON
serialization time. No span is exported anywhere; they exist to correlate this
application's own logs and, for an external caller that propagates W3C trace
context (see [ADR 0009](../../../adr/0009-trace-correlated-structured-logging.md)), with that caller's own logs or traces.

## Sensitive-data policy

`url.query` redacts values for `access_token`, `client_assertion`,
`client_secret`, `code`, `code_verifier`, `id_token`, `id_token_hint`,
`logout_token`, `refresh_token`, `session_state`, and `state`. The list is
source-controlled in `WebSecurityConfiguration.QUERY_PARAMETER_REDACT_LIST`.

The request/security filters intentionally do not emit request or response
bodies, headers, cookies, session IDs, passwords, keys, tokens, client secrets,
or exception messages from security failures. `url.full` and `url.original` are
also omitted to avoid preserving an unreviewed raw URL.

## Consumer guidance

Use `http.request.id` to join request lifecycle and in-request audit events;
use `trace.id` to correlate this application's logs with an external caller's
own logs or traces, when one propagates W3C trace context. Prefer
`event.category`, `event.action`, and `event.outcome` to text matching.
Treat `url.path`, `url.query`, `user.name`, and `source.ip` as potentially
sensitive operational data and apply appropriate access and retention controls.
