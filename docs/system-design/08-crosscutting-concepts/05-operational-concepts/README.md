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
it.

<!-- arc42-manual: Record how each deployment environment actually supplies CERTIFICATE_PEM/PRIVATE_KEY_PEM/CA_BUNDLE_PEM, the Keycloak issuer-uri, and any database connection properties (not shown in application.yaml, so presumably supplied entirely by the deployment platform) -->

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
