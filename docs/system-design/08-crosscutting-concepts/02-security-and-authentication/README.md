<!-- arc42-generated -->
# Security and Authentication

The application delegates authentication to Keycloak as an OpenID Connect
(OIDC) relying party and owns application-level authorization itself, on top
of a local user/group/role model (see
[Domain Model](../01-domain-model/README.md#entity-model)). Every security
control below is recorded as a control-implementation mapping against a
specific external standard, so the standard, not this page, is the source of
truth for what "complete" means; this page explains how the pieces fit
together and where to find the control-by-control evidence.

## Identity and the trust boundary

Keycloak owns credential collection, password policy, hashing, brute-force
protection, and (optionally) multi-factor authentication; the application
never receives, stores, or compares a password. The application's own
responsibility begins at the OIDC `preferred_username` claim: it resolves
that claim to the immutable local `username` field and denies authentication
outright for a missing claim, an unknown local user, or a disabled local
user. Keycloak realm and client roles are never translated into application
authorities; a successful OIDC authentication only proves identity, not
permission.

Each Keycloak client is configured for `private_key_jwt` client
authentication rather than a shared client secret: `WebSecurityConfiguration`
loads a JWKS containing the application's signing (and optionally
encryption) key pair, `RestClientAuthorizationCodeTokenResponseClient` signs
a client assertion with the private key during token exchange, and
`JwksController` publishes only the public components at `/oauth2/jwks` for
Keycloak to verify against. The development JWKS fixture at
`src/test/resources/jwks.json` contains private key material and is not
packaged; `app.jwks` has no default, so a real deployment must supply its
own JWKS through it (startup fails otherwise) and rotates keys in
coordination with Keycloak. ID tokens are validated by a
custom `JwtDecoderFactory<ClientRegistration>` (needed because
`OidcIdTokenDecoderFactory` cannot be customized enough for encrypted
ID-token support), which accepts only signed RS256 tokens, selects only
JWKS keys marked for signature use, and applies `OidcIdTokenValidator`.
Logout is two-directional: `OidcClientInitiatedLogoutSuccessHandler` drives
relying-party-initiated logout through Keycloak's `end_session_endpoint`,
and the application also accepts Keycloak's back-channel logout
notifications via `http.oidcLogout(oidcLogout -> oidcLogout.backChannel(...))`.
The full control-by-control mapping against the OWASP Authentication Cheat
Sheet, including which rows are delegated to Keycloak and which require a
production identity-provider decision, is in
[Authentication](authentication.md).

## From identity to permission

Authorization is deliberately a separate model from authentication and is
owned entirely by the application. A user never receives a role directly;
roles are granted only through group membership
(`AppUser -> AppGroup -> AppRole`, see
[Domain Model](../01-domain-model/README.md)), and each effective role is
exposed as a Spring Security authority by prepending `ROLE_` at login. The
one irregular case, `ROLE_MANAGE` (whose stored name already begins with
`ROLE`), is checked with `hasAuthority("ROLE_ROLE_MANAGE")` rather than
`hasRole(...)`, to avoid Spring Security double-prefixing it.

What makes this model different from a typical "authorities computed once at
login" setup is that `LocalAuthorityRefreshFilter` reloads a user's `ROLE_`
authorities from the local user/group/role tables on every request. A group's
role set changing, or a role being deleted, therefore takes effect for every
affected member's very next request rather than only at their next login.
Two specific changes go further and terminate the session immediately rather
than waiting for the next request: `SessionRevocationService` revokes a
user's session as soon as an administrator disables their account, deletes
it, or changes their group membership (see
[ADR 0015](../../../adr/0015-per-request-local-authority-refresh.md)). The
management API itself is gated per resource family, one authority per
admin controller (`ROLE_USER_MANAGE`, `ROLE_GROUP_MANAGE`,
`ROLE_ROLE_MANAGE`), enforced with `@PreAuthorize` at the controller layer
so the service layer can trust that a caller reaching it is already
authorized (see [Architecture Patterns](../03-architecture-patterns/README.md#authorization-checks-at-the-layer-boundary)).
The full control-by-control mapping against the OWASP Authorization Cheat
Sheet, including the explicit trade-off of role-based access control over
ABAC/ReBAC, is in [Authorization](authorization.md).

## Session handling

Spring Security's servlet `HttpSession` is backed by Spring Session JDBC
rather than an in-memory or sticky-session store: the browser holds only an
opaque `id` cookie (`HttpOnly`, `SameSite=Lax`, `Secure` outside `local` and
`test`), session metadata lives in `SPRING_SESSION`, and serialized
attributes live in `SPRING_SESSION_ATTRIBUTES`, owned by Liquibase rather
than Spring Boot's own JDBC session-schema initializer (see
[ADR 0004](../../../adr/0004-database-schema-management.md)). This is what
lets any instance service any request for an existing session (see
[Operational Concepts](../05-operational-concepts/README.md#scaling-and-statelessness-posture)):
`SpringSessionBackedSessionRegistry` enforces the one-concurrent-session-per-user
rule by querying the shared JDBC store rather than an in-memory registry
local to one instance. Idle timeout is 15 minutes; a separate
`AbsoluteSessionTimeoutFilter` enforces a hard 12-hour maximum regardless of
activity. Session lifecycle events (audit-ID initialization, session-fixation
renewal, logout, absolute timeout, concurrent-session expiry) are logged
with a random, application-local `session.id`, deliberately never the cookie
or raw Spring Session ID. The full control-by-control mapping against the
OWASP Session Management Cheat Sheet is in [Sessions](sessions.md).

## Errors, headers, and what the client sees

Every error response, whether raised by application code, rejected by
Spring Security's method security, rejected by Spring Security's
`HttpFirewall` before the filter chain even runs, or rejected by Tomcat
itself before Spring Security runs, converges on one shape: RFC 9457
Problem Details (`application/problem+json`), with a stable `type` URN as
the machine-readable contract and no internal detail, stack trace, or
rejected value ever included in the body. `ApiResponseEntityExceptionHandler`
is the single place that maps an exception type to an HTTP status, a
`urn:problem:*` type, and a redacted audit event; two earlier layers
(`ProblemDetailRequestRejectedHandler` for firewall rejections,
`TomcatProblemDetailErrorReportValve`/`ProblemDetailErrorController` for
failures that never reach Spring MVC) produce the same shape for failures
that occur before that handler can run. The full type-by-type mapping is in
[Error responses](error-responses.md).

Response headers are configured explicitly where the OWASP HTTP Security
Response Headers Cheat Sheet calls for an application decision (a restrictive
`Content-Security-Policy` of `default-src 'none'` reflecting that this is a
JSON API with no rendered UI, `Referrer-Policy: strict-origin-when-cross-origin`,
a `Permissions-Policy` denying camera/geolocation/microphone/payment/USB),
and left to Spring Security's own defaults where those defaults already
satisfy the recommendation (`X-Content-Type-Options: nosniff`,
`Cache-Control: no-store`, HSTS on secure responses). The full header-by-header
mapping, including which values are a deployment decision rather than an
application one (`Strict-Transport-Security` tuning, the `Server` header),
is in [HTTP security headers](headers.md).

## Platform hardening

Two broader, standards-based control implementations sit alongside the
request-handling controls above rather than repeating them:
[Hardening](hardening.md) maps the application's embedded-Tomcat
configuration (TRACE disabled, no `Server` header, TLS 1.2/1.3 only with
explicit AEAD cipher suites, strict servlet compliance, facade discarding,
no symbolic linking, the separate Actuator management port) against the CIS
Apache Tomcat 11 Benchmark, translating each standalone-Tomcat control into
either "configured," "not applicable to embedded Tomcat," or a deployment
decision; and [ASVS](asvs.md) maps all 345 OWASP ASVS 5.0.0 requirements
against the codebase across encoding/sanitization, validation, the web
frontend, the API surface, file handling, and authentication. A minority
of them resolve to "not applicable to this template" (no HTML
rendering, no file upload, no GraphQL, no WebSocket) because the template is
a narrow JSON administration API rather than a general-purpose web
application; most of the rest are met by the template or its frameworks, or
delegated to Keycloak or the deployment, and the remainder are recorded as
partial or not yet implemented.
Neither document repeats the authentication, authorization, session, or
header detail already recorded in the pages above; they cite it instead.

## Required production decisions

Each per-control document ends with its own list of decisions a template
adopter must make and record before production use (MFA policy,
`__Host-`/`__Secure-` cookie prefixes, mTLS for machine clients, HSTS
preload, TLS termination topology, and more); they are intentionally kept
next to the control they affect rather than collected here. Two decisions
recur across every page and are worth naming once: the production Keycloak
realm's brute-force, MFA, and password-recovery policy, and whether any
administrative action should require re-authentication or step-up MFA, an
open item recorded in both [Authentication](authentication.md#owasp-control-implementation)
and [Sessions](sessions.md#owasp-control-implementation).

<!-- arc42-manual: Record the production Keycloak realm topology, MFA policy, and TLS termination decisions once they are finalized for a real deployment, rather than leaving them as open items scattered across the per-control pages. -->
<!-- /arc42-generated -->
