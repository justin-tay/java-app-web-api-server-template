<!-- arc42-generated -->
# Operational concepts

How the running application is configured, monitored, and scaled. Session
and cache *security* posture is covered in
[Sessions](../02-security-and-authentication/sessions.md); this page covers the operational shape
around it: externalized configuration, the management surface, and what
scaling an instance up or down actually requires.

## Configuration management

Configuration is layered Spring Boot YAML, selected by Spring profile, with
no configuration server or external config store in this template:

| File | Scope | Notable differences from the default |
| --- | --- | --- |
| `commons-defaults.yaml` (in commons) | Always applied, below every application file | Shared secure baseline: TLS 1.2/1.3 protocols and AEAD cipher suites, the `id` session cookie (`HttpOnly`, `SameSite=Lax`, `Secure`, cookie-only tracking) with a 15-minute idle and 12-hour absolute timeout, `management.server.port: 8082` with only `health` exposed under `/app`, ECS logging with `environment: production` and trace correlation. An application overrides any key by setting it. |
| `application.yaml` | Always applied | Production-shaped application settings: TLS certificate bundle, the Keycloak client registration. |
| `application-local.yaml` | `local` profile | Disables `server.ssl.enabled` and the `Secure` cookie attribute for HTTP-only local development; sets `environment: local` in logs. |
| `application-test.yaml` | `test` profile (active during `mvn test`/`verify`) | Same TLS/cookie relaxation as `local`, plus `management.server.port: 0` so the management port binds to an OS-assigned ephemeral port instead of the fixed `8082`, avoiding collisions between parallel test JVMs; sets `environment: test` in logs. |

TLS key material is never stored in a properties file. `server.ssl.bundle`
points at a PEM bundle named `server`, whose certificate, private key, and
CA bundle are each read from an environment variable
(`CERTIFICATE_PEM`, `PRIVATE_KEY_PEM`, `CA_BUNDLE_PEM`) with an empty default,
so the deployment platform (secret store, orchestrator-injected secret,
etc.) supplies the actual values; see
[ADR 0007](../../../adr/0007-tls-and-oauth-client-key-management.md) for the
key-management rationale. The OAuth2 client's Keycloak `issuer-uri` in
`application.yaml` is a local-development placeholder
(`http://localhost:8080/realms/test`); a non-local deployment must override
it. The application does not need Keycloak to start: its metadata is fetched
on first use, login answers 503 with `Retry-After` until that succeeds, and
the `oidcDiscovery` health contributor keeps the readiness group DOWN until
it has. `commons.security.oauth2.discovery.retry-interval` (default 30
seconds), `connect-timeout` (2 seconds) and `read-timeout` (5 seconds) tune
the retries; a mistyped `issuer-uri` that Keycloak answers with a client error
still fails startup ([ADR 0029](../../../adr/0029-lazy-oidc-discovery.md)). A
Keycloak whose certificate is issued by a private CA is trusted by naming an
SSL bundle that holds that CA in
`commons.security.oauth2.client.provider.<id>.ssl-bundle`; it covers discovery,
the token endpoint, the JWK Set and the user info endpoint, and a certificate
that is not trusted fails startup instead of reading as Keycloak being down.

<!-- arc42-manual: Record how each deployment environment actually supplies CERTIFICATE_PEM/PRIVATE_KEY_PEM/CA_BUNDLE_PEM, the Keycloak issuer-uri, and any database connection properties (not shown in application.yaml, so presumably supplied entirely by the deployment platform) -->

## Scheduled jobs and application settings

Two scheduled jobs run in every instance, and are safe to run on several at once:

| Job | Does | Controlled by |
| --- | --- | --- |
| `InactiveUserSuspender` | Suspends accounts not in use for `inactivity.suspendAfterDays` and removes those not in use for `inactivity.removeAfterDays` | The `inactivity.*` settings; the check interval is `commons.accounts.inactivity.check-interval` (one hour by default) |
| `AccountReviewScheduler` | Creates the privileged and the non-privileged account review task on the first run in each one's review month (a month whose number minus one is a multiple of `review.privilegedIntervalMonths` or `review.nonPrivilegedIntervalMonths`, so every three months gives January, April, July and October), and completes tasks that a change outside the review has finished | The `review.*` settings; the check interval is `commons.accounts.review.check-interval` (one hour by default) and the time zone that decides the month is `commons.accounts.review.time-zone` (the system time zone by default) |

The policy is not in configuration files. It is six rows of the `app_setting`
table, read on every run and edited at `/admin/settings` by whoever holds `settings:update`, so a change needs no deployment and is audited. The migration seeds
them on: suspend after 90 days, remove after 180, review the privileged accounts every month and the others every 12 months (each interval can be 1, 3, 6 or 12, always counted from January, and the non-privileged one cannot be shorter than the privileged one). The
sample application's `dev` data turns both jobs off so its fixtures are never
suspended, removed, or put into a review ([ADR 0031](../../../adr/0031-inactive-account-suspension-and-removal.md),
[ADR 0032](../../../adr/0032-periodic-account-review.md), [ADR 0037](../../../adr/0037-account-review-populations-and-stored-report.md), [ADR 0038](../../../adr/0038-role-permission-model-and-account-review-classes.md)). Upgrading gives every
existing account a fresh inactivity clock, so nobody is suspended or removed on the
first run. Removal is permanent: the audit trail and the frozen review records are the only
record afterwards, and nothing purges them. A completed review is a stored PDF report with its
SHA-256 in the audit trail, written once; the xlsx and csv are regenerated from the frozen
records on request. Several review tasks can be open at once, because an unfinished task stays open and overdue
when the next review month starts.

## Management and health surface

Spring Boot Actuator runs on its own embedded server, `management.server.port:
8082`, separate from the application's `server.port: 8081`, under the
non-default base path `/app` rather than `/actuator`. Only `health` is
exposed, with `show-details`/`show-components: never`, so an unauthenticated
request returns nothing but `{"status":"UP"}`. This is the only endpoint a
load balancer or orchestrator health check should target; there is no
`metrics`, `info`, `env`, or other operational endpoint to scrape today. The
control-by-control rationale (why the port is separate, why the path is
non-default, what adding another endpoint would require) is maintained once
in [Hardening](../02-security-and-authentication/hardening.md#actuator-management-port) and
[ADR 0014](../../../adr/0014-actuator-management-port.md); operationally,
the two things a deployer must still do are (1) restrict the management port
to the load balancer's health-check path and any internal ops network, at
the security-group/NACL layer, and (2) never place the management and
application ports behind the same target group or listener.

Distributed tracing sampling is set to `probability: 1.0`
(`management.tracing.sampling.probability`) with no span exporter
configured anywhere in the template, so every log line carries `trace.id`/
`span.id` (see [Logging](../06-logging-and-monitoring/README.md)) at effectively no export
cost, since nothing is exported; a deployment that adds an OTLP or similar
exporter should revisit this probability against the resulting export
volume.

## Client IP behind proxies

Every log event records `source.ip`, the immediate peer from
`HttpServletRequest.getRemoteAddr()`, which behind a load balancer is the load
balancer. The end-user address, `client.ip`, is recorded only when the
application defines a `ClientIpResolver` bean, because which forwarded header
can be trusted depends on the deployment's proxies, and choosing it is the
deployer's responsibility. By default commons resolves none.

| Deployment | Resolver | Notes |
| --- | --- | --- |
| CloudFront, then an Application Load Balancer | `new CloudFrontViewerAddressClientIpResolver()` | Recommended. CloudFront sets `CloudFront-Viewer-Address` from the viewer's TCP connection, independent of any `X-Forwarded-For` the client sends. |
| CloudFront, then an Application Load Balancer, without the viewer-address header | `XForwardedForClientIpResolver.fromRight(2)` | Both append to `X-Forwarded-For`: CloudFront the viewer, then the load balancer the CloudFront edge. |
| An Application Load Balancer alone | `XForwardedForClientIpResolver.fromRight(1)` | The load balancer appends the client to `X-Forwarded-For` in its default `append` mode. |
| An edge proxy that replaces `X-Forwarded-For` with the address it received the request from | `XForwardedForClientIpResolver.leftmost()` | For example nginx with `proxy_set_header X-Forwarded-For $remote_addr`. |
| Several proxies of your own, with changing hop counts | `new XForwardedForClientIpResolver(proxyCidrs)` | Every proxy that appends to the header must be in `proxyCidrs`. |
| A proxy that sets a single-address header and replaces any value a client sent | `new TrustedHeaderClientIpResolver("True-Client-IP")` | Any header name the proxy sets. |

Choose the leftmost entry only behind an edge that replaces the header.
CloudFront and Application Load Balancers do not: both append to an
`X-Forwarded-For` the client sends, so behind them the leftmost entry is
whatever the client wrote. An Application Load Balancer cannot be configured to
replace the header either; its `routing.http.xff_header_processing.mode` is
`append`, `preserve`, or `remove`, and `remove` drops the header entirely. A
CloudFront Function on the viewer request can delete a client-supplied
`X-Forwarded-For`, after which CloudFront forwards one holding only the viewer
address; confirm that with a test request before relying on `leftmost()`.

Each resolver also takes the CIDR blocks of the application's immediate peer,
such as the load balancer's subnets (a load balancer's node addresses change,
its subnets do not), and then ignores the header on a request from any other
peer. That guards against a request that reaches the application without
passing through its load balancer. It does not guard against a request that
bypasses the edge but reaches the load balancer, which still arrives from the
load balancer, so the controls that make `client.ip` trustworthy are in the
network:

1. Allow the application's port only from the load balancer's security group.
2. Behind CloudFront, allow the load balancer's listener only from the
   `com.amazonaws.global.cloudfront.origin-facing` managed prefix list, and add
   a secret custom origin header in CloudFront that a load balancer listener
   rule requires, because the prefix list admits every CloudFront distribution,
   including one an attacker points at the load balancer.
3. Add `CloudFront-Viewer-Address` to the distribution's origin request policy;
   CloudFront sends it only then.

The resolvers accept only a literal IP address: a dotted-quad IPv4 address
without leading zeros, or an IPv6 literal, and never resolve a host name. They
accept `CloudFront-Viewer-Address` with an IPv6 address with or without
brackets, and an `X-Forwarded-For` entry with the port an Application Load
Balancer adds when client port preservation is enabled. An `X-Forwarded-For`
resolver parses only the entries from the right up to the one it selects, so a
malformed value a client prepends cannot suppress `client.ip`.

## Scaling and statelessness posture

The application is not a stateless service in the strict sense: user
authentication state lives in a server-side session, not a self-contained
bearer token. It is, however, built to scale horizontally without sticky
sessions, because session state itself is externalized:

* Session storage is JDBC-backed (Spring Session,
  [ADR 0006](../../../adr/0006-jdbc-backed-server-side-sessions.md)), so any
  instance can service any request for an existing session; the browser
  cookie carries only an opaque ID, never instance-affine data.
* Concurrent-session enforcement uses
  `SpringSessionBackedSessionRegistry`, which finds a user's session by
  querying the shared JDBC store rather than an in-memory registry local to
  one instance, so the one-concurrent-session-per-user rule holds correctly
  across instances (see [Sessions](../02-security-and-authentication/sessions.md)).
* Authorization state is not cached in the session at all:
  `LocalAuthorityRefreshFilter` reloads a user's roles from the database on
  every request
  ([ADR 0015](../../../adr/0015-per-request-local-authority-refresh.md)), so
  scaling out introduces no risk of one instance acting on stale
  authorities cached by another.
* There is no in-process or distributed application cache (no `@Cacheable`,
  no cache manager) anywhere in the codebase to keep coherent across
  instances; the only shared mutable state an instance depends on is the
  database itself.
* The application has no DDL privileges at runtime
  ([ADR 0004](../../../adr/0004-database-schema-management.md)); a new
  instance starting up performs no schema migration itself; a separate CI
  migration job applies Liquibase changesets ahead of rollout. This means
  scaling out (or a rolling deployment) never risks two instances racing to
  apply the same schema change.

The practical consequence: horizontal scaling requires only that every
instance point at the same database (for both the domain schema and
`SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES`); it does not require sticky
load-balancer routing, a shared in-memory cache, or a distributed lock for
anything this template currently does.

<!-- arc42-manual: Record the deployment platform's actual scaling policy (min/max instance count, autoscaling trigger, rolling-deployment strategy) and confirm the database connection pool is sized for the maximum expected instance count. -->
<!-- /arc42-generated -->
