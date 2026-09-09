# Security logging schema

The application writes JSON using Spring Boot's ECS formatter. This is a
field-reference for the events created by the template, modelled after the
[Elastic Common Schema field reference](https://www.elastic.co/docs/reference/ecs/ecs-field-reference).
It separates ECS fields from the two project extensions so consumers can map
or index them deliberately.

The formatter also includes fields that are normally supplied by the runtime,
such as `@timestamp`, `log.level`, `log.logger`, `process.pid`, and
`process.thread.name`. `service.name` comes from `spring.application.name`;
`service.environment` is configured per Spring profile.

## Log-level rationale

Security failures caused by a client are logged at `WARN`: invalid credentials,
denied access, and failed CSRF validation mean the security control worked and
rejected the request. They are important for investigation, but they are not
application faults.

`ERROR` is reserved for cases where the application cannot perform the security
control itself—for example, a key cannot be retrieved, cryptographic
verification fails unexpectedly, an authorization dependency is unavailable, or
required audit logging cannot be delivered.

Logging every 401 or 403 as `ERROR` would make routine mistakes, expired
sessions, and automated probes appear to be server failures, creating noisy
operational alerts.

## Event families

| Event family | Logger | Level | `event.category` | `event.type` | `event.action` | `event.outcome` |
| --- | --- | --- | --- | --- | --- | --- |
| Request received | `RequestLoggingFilter` | INFO | `web` | `access`, `start` | `receive_request` | Not set: no response exists yet. |
| Request completed | `RequestLoggingFilter` | INFO | `web` | `access`, `end` | `complete_request` | `success` for status below 400; otherwise `failure`. |
| Authentication success | `SecurityAuditEventLogger` | INFO | `authentication` | `info` | `login` | `success` |
| Authentication failure | `SecurityAuditEventLogger` | WARN | `authentication` | `denied` | `login` | `failure` |
| Authorization denial | `SecurityAuditEventLogger` | WARN | `web`, `api` | `access`, `denied` | `authorize_access` | `failure` |
| CSRF denial | `ProblemDetailAccessDeniedHandler` | WARN | `web`, `api` | `access`, `denied` | `validate_csrf_token` | `failure` |
| Logout success | `SecurityAuditEventLogger` | INFO | `authentication` | `info` | `logout` | `success` |
| Unexpected application error | `ApiResponseEntityExceptionHandler` | ERROR | `web` | `error` | `process_request` | `failure` |
| Application starting | `ApplicationLifecycleEventLogger` | INFO | `process` | `start` | `start_application` | `unknown` |
| Application started | `ApplicationLifecycleEventLogger` | INFO | `process` | `start` | `start_application` | `success` |
| Application failed to start | `ApplicationLifecycleEventLogger` | ERROR | `process` | `start` | `start_application` | `failure` |
| Application stopped | `ApplicationLifecycleEventLogger` | INFO | `process` | `end` | `stop_application` | `success` |

## `event.action` naming and semantics

`event.action` identifies the security-relevant operation observed or attempted
by the application. Use a stable, lower-snake-case verb phrase, for example
`login`, `logout`, `authorize_access`, `validate_csrf_token`, or
`validate_input`.

Keep the action separate from its classification, result, reason, and target:

| Field | Semantic role | Example |
| --- | --- | --- |
| `event.action` | Operation attempted or performed | `validate_csrf_token` |
| `event.type` | Normalized event classification | `["access", "denied"]` |
| `event.outcome` | Final result from the application's perspective | `failure` |
| `error.type` | Safe, stable technical reason | `CsrfException` |
| Target fields | Resource affected by the operation | `http.route`, `url.path`, `user.name` |

Do not encode the result in `event.action`. For example,
`input_validation_failure` combines an operation and a result that are already
represented independently by `event.type` and `event.outcome`. That duplicates
semantics, fragments queries (`validate_input` versus
`input_validation_failure`), and can create contradictory records if the
fields later diverge.

Prefer:

```json
{
  "event.action": "validate_input",
  "event.type": ["access", "denied"],
  "event.outcome": "failure"
}
```

The same convention explains the current CSRF event:

```json
{
  "event.action": "validate_csrf_token",
  "event.type": ["access", "denied"],
  "event.outcome": "failure",
  "error.type": "CsrfException",
  "http.response.status_code": 403
}
```

## Category-to-field matrix

The [ECS allowed event-category values](https://www.elastic.co/docs/reference/ecs/ecs-allowed-values-event-category)
describe `event.category` as an array-capable categorization field. This
application currently emits only ECS allowed values: `web`, `api`, `authentication`, and
`process`. The authorization-denial events use both `web` and `api` to
describe an API request denied at the web boundary. This permits the ECS
expected `access` and `denied` types for `api`, while retaining the web-access
view for dashboards.

Every row below also includes the runtime baseline fields: `@timestamp`,
`ecs.version`, `message`, `log.level`, `log.logger`, `process.pid`,
`process.thread.name`, `service.name`, and `service.environment`.

| Category | Event/action | Fields logged in addition to the runtime baseline | ECS alignment |
| --- | --- | --- | --- |
| `web` | `receive_request` | `event.category`, `event.type`, `event.action`, `event.start`, `http.request.id`, `http.request.method`, `url.scheme`, `server.address`, `server.port`, `url.path`; conditional `url.query`, `url.query_keys`, and `user.name`. | `web` is allowed. `access` is an expected type; `start` is additionally used to express the lifecycle boundary. |
| `web` | `complete_request` | `event.category`, `event.type`, `event.action`, `event.start`, `event.end`, `event.duration`, `event.outcome`, `http.request.id`, `http.request.method`, `http.response.status_code`, `url.scheme`, `server.address`, `server.port`, `url.path`, `http.route`; conditional `url.query`, `url.query_keys`, and `user.name`. | `web` is allowed. `access` is an expected type; `end` is additionally used to express the lifecycle boundary. `http.route` and `url.query_keys` are project extensions. |
| `authentication` | Successful `login` | `event.category`, `event.type`, `event.action`, `event.outcome`, `user.name`; conditional `http.request.id` from MDC. | `authentication` is allowed and `info` is an expected type. |
| `authentication` | Failed `login` | `event.category`, `event.type`, `event.action`, `event.outcome`, `user.target.name`, `error.type`; conditional `http.request.id` from MDC. | `authentication` is allowed. ECS lists `start`, `end`, and `info` as expected types for this category; the current `denied` value is a project convention for a failed authentication. |
| `authentication` | Successful `logout` | `event.category`, `event.type`, `event.action`, `event.outcome`, `user.name`; conditional `http.request.id` from MDC. | `authentication` is allowed and `info` is an expected type. |
| `web`, `api` | Denied `authorize_access` | `event.category`, `event.type`, `event.action`, `event.outcome`, `user.name` (uses `anonymous` when no authentication is available); conditional `http.request.id` from MDC. | Both are ECS allowed categories. `api` expects `access` and `denied`, which are emitted together. The final HTTP status is intentionally left to the correlated request-completed event because this listener does not determine it. |
| `web`, `api` | Failed `validate_csrf_token` | `event.category`, `event.type`, `event.action`, `event.outcome`, `http.response.status_code`, `url.path`, `source.ip`, `error.type`; conditional `http.request.id` from MDC. | Both are ECS allowed categories. The handler itself always writes 403, so this event safely records `http.response.status_code=403`. `source.ip` is the direct peer address, not a proxy-normalized client IP. |
| `web` | Failed `process_request` | `event.category`, `event.type`, `event.action`, `event.outcome`, `http.response.status_code`, `url.path`, `error.type`, and exception stack trace; conditional `http.request.id` from MDC. | `web` and `error` are ECS allowed values. This event is emitted only for unexpected exceptions handled during Spring MVC dispatch and upstream 5xx `RestClientResponseException` values. |
| `process` | `start_application` / `stop_application` | `event.category`, `event.type`, `event.action`, `event.outcome`; failed startup also has `error.type` and `error.stack_trace`. | `process`, `start`, and `end` are ECS allowed values. The listener is registered through `spring.factories`, so it observes startup failure before a Spring bean can be created. No application-lifecycle record is possible before logging initializes. |

`http.request.id` is established by `SecurityLoggingContextFilter` and normally
appears through MDC. It is shown as conditional because security events can be
published outside a servlet request. `url.query` and `url.query_keys` appear
only when the request has a non-blank query string.

## ECS-standard fields emitted by this template

Types and descriptions follow ECS. A field is marked conditional when it is
present only for the event family where it has meaning.

| Field | ECS type | Presence | Description and source |
| --- | --- | --- | --- |
| `@timestamp` | `date` | Every event | Formatter timestamp for when the log event was emitted. |
| `ecs.version` | `keyword` | Every event | ECS version emitted by the formatter. |
| `message` | `match_only_text` | Every event | Human-readable event message, for example `HTTP request completed`. Use structured fields for queries. |
| `log.level` | `keyword` | Every event | Logger severity (`INFO` or `WARN` for the template's security events). |
| `log.logger` | `keyword` | Every event | Java logger name. |
| `process.pid` | `long` | Runtime supplied | JVM process identifier. |
| `process.thread.name` | `keyword` | Runtime supplied | Thread emitting the event. |
| `service.name` | `keyword` | Runtime supplied | Spring application name: `java-app-web-api-server`. |
| `service.environment` | `keyword` | Runtime supplied | `production` by default; overridden to `local` and `test` by those profiles. |
| `event.category` | `keyword` (array-capable) | Template events | High-level event family. The template uses only ECS allowed values: `web`, `api`, `authentication`, and `process`. |
| `event.type` | `keyword` (array-capable) | Template events | Lifecycle/subcategory such as `access`, `start`, `end`, `info`, or `denied`. Every template event emits an array, including events with one type. See the category matrix for expected-value compatibility. |
| `event.action` | `keyword` | Template events | Stable action identifier listed in the event-family table. |
| `event.outcome` | `keyword` | Completed/audit events | `success` or `failure`; absent from the received request event. |
| `event.start` | `date` | Request lifecycle events | UTC instant captured when the request enters `RequestLoggingFilter`. The received and completed events use the same value. |
| `event.end` | `date` | Completed request | UTC instant captured immediately before emitting completion. |
| `event.duration` | `long` | Completed request | Nanoseconds between `event.start` and `event.end`. This makes the lifecycle fields internally consistent. |
| `http.request.id` | `keyword` | Request lifecycle events and MDC-backed events | Request correlation ID. Uses `X-Amz-Cf-Id` when supplied; otherwise a server-generated UUID. It is for correlation only, not authentication. |
| `http.request.method` | `keyword` | Request lifecycle events | Incoming servlet request method. |
| `http.response.status_code` | `long` | Completed request | Final servlet response status. |
| `url.scheme` | `keyword` | Request lifecycle events | Servlet request scheme, normally `http` or `https`. |
| `url.path` | `wildcard` | Request lifecycle events and CSRF denial | Request URI path. Do not place secrets in path segments. |
| `url.query` | `keyword` | Requests with a query string | Reconstructed decoded query string after redaction. It excludes the leading `?`, per ECS. |
| `server.address` | `keyword` | Request lifecycle events | Servlet server name/address as seen by the application. It is not necessarily the public host behind a proxy. |
| `server.port` | `long` | Request lifecycle events | Servlet server port. |
| `user.name` | `keyword` | Authenticated request/audit events | Authenticated principal name when available. It is not set for anonymous request lifecycle events. |
| `user.target.name` | `keyword` | Failed authentication | Account named in the failed sign-in attempt. It is a target rather than an authenticated principal. |
| `source.ip` | `ip` | CSRF denial | `HttpServletRequest.getRemoteAddr()`. Behind a proxy this is commonly the proxy address; it is not a normalized end-user client IP. |
| `error.type` | `keyword` | Authentication failures, CSRF denials, and unexpected application errors | Simple class name for the security events; fully qualified exception class name for unexpected application errors. Exception messages are deliberately not added as structured fields. |
| `error.stack_trace` | `wildcard` | Unexpected application errors | Stack trace attached as the logger cause. This is for protected operator access and is never returned to the caller. |

## Project extension fields

The formatter will serialize any dotted key-values, but these fields are not
defined by ECS. Consumers should add mappings/templates for them or retain
them as JSON extensions.

| Field | Type | Emitted by | Purpose and handling |
| --- | --- | --- | --- |
| `http.route` | `keyword` | Completed request | Spring MVC best-matching route pattern, such as `/api/widgets/{id}`. `UNKNOWN` means that no handler pattern was available (for example, an unmatched route). This is a project extension; it is useful for aggregation without logging a raw identifier-bearing path. |
| `url.query_keys` | `keyword[]` | Requests with a query string | Names of all query parameters, including parameters whose values are redacted. This supports diagnostics without exposing their values. It is a project extension, not an ECS URL field. Parameter names themselves should not contain sensitive data. |

## Correlation and lifecycle semantics

One request normally produces exactly two lifecycle events sharing
`http.request.id` and `event.start`:

1. `receive_request` establishes that the request entered the application.
2. `complete_request` records the final status and the duration. Its
   `event.duration` is exactly `event.end - event.start` in nanoseconds.

The filters support asynchronous dispatch. The request ID and authenticated
user are retained so the completion event remains correlated even when it is
emitted on a different thread. Other security audit events that occur during a
request inherit the ID through MDC when applicable.

## Query-string and sensitive-data policy

`url.query` is useful for support but can contain credentials. Before it is
emitted, values for these OAuth/OIDC parameter names are replaced with
`[REDACTED]`:

`access_token`, `client_assertion`, `client_secret`, `code`, `code_verifier`,
`id_token`, `id_token_hint`, `logout_token`, `refresh_token`, `session_state`,
and `state`.

The source-controlled list is
`WebSecurityConfiguration.QUERY_PARAMETER_REDACT_LIST`. Update it as part of
the code change whenever a new URL parameter may contain a credential or other
sensitive value. The application intentionally does **not** emit these fields:

| Not emitted by the request/security filters | Reason |
| --- | --- |
| Request and response bodies | May contain credentials, personal data, or regulated data; large bodies also harm log availability. |
| `Authorization` and other request headers | Frequently contain bearer tokens, cookies, or client metadata. |
| Cookies and session identifiers | Authentication material must not enter logs. |
| Passwords, client secrets, keys, tokens, or exception messages from security failures | Prevents direct secret disclosure and limits accidental personal-data logging. |
| `url.full` and `url.original` | Avoids storing an unreviewed raw URL. The template records only split fields and a redacted query string. |
| Generic client IP | Proxy-trust policy is deployment-specific. Only the CSRF event currently records the direct peer address as `source.ip`. |

## Consumer guidance

Use `http.request.id` to join the received event, completion event, and
in-request audit events. Prefer `event.category`, `event.action`, and
`event.outcome` over text matching `message`. Treat `url.query`,
`url.path`, `user.name`, and `source.ip` as potentially sensitive
operational data and apply appropriate access and retention controls in the
collector.
