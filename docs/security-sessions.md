# Session security

This document records the session-management posture of the application against
the [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
It describes the application configuration; the production deployment must also
verify the effective cookie and HTTPS headers at its public edge.

## Design and ownership

Spring Security uses the servlet `HttpSession` for the security context, OAuth2
authorization request, saved request, and normally the OAuth2 authorized-client
state. Spring Session replaces that servlet session with JDBC-backed storage:

* The browser holds only the opaque `id` cookie.
* Session metadata is stored in `SPRING_SESSION`.
* Serialized session attributes are stored in `SPRING_SESSION_ATTRIBUTES`.
* Spring Boot auto-configures Spring Session JDBC through
  `spring-boot-starter-session-jdbc`. Liquibase owns those tables, and Spring
  Boot's JDBC session-schema initializer is disabled. See
  [ADR 0001](adr/0001-database-schema-management.md).

The application database account must have only the data-access permissions
needed by the running service. The CI migration account owns DDL and migration
data changes. Production database access, encryption at rest, backups, and
monitoring must protect session rows because they can contain security and
OAuth2 state.

## Current configuration

| Setting | Value | Security purpose |
| --- | --- | --- |
| Session store | Spring Session JDBC | Keeps session state and OAuth2 flow state server-side. |
| Cookie configuration | `server.servlet.session.cookie.*` | Spring Boot applies these properties to Spring Session's cookie serializer. |
| Cookie name | `id` | Avoids disclosing the servlet/framework default through the cookie name. |
| Exchange mechanism | Cookie only | URL rewriting is disabled by `tracking-modes: COOKIE`. |
| `HttpOnly` | `true` | Prevents JavaScript from reading the session cookie. |
| `Secure` | `true` outside `local` and `test` | Prevents the production cookie being sent over HTTP. Local/test HTTP is an explicit development exception. |
| `SameSite` | `Lax` | Adds CSRF defence while allowing the top-level OIDC redirect from Keycloak back to the application. |
| Idle timeout | 15 minutes | Server-side inactivity expiry. |
| Absolute timeout | 12 hours | Server-side maximum session lifetime, regardless of activity. |
| Session schema | Liquibase changeset `003-spring-session-schema.sql` | Prevents schema creation at application startup. |

The cookie is non-persistent because no `Max-Age` or `Expires` value is
configured. No session ID, OAuth token, or credential is deliberately stored in
browser `localStorage` or `sessionStorage`.

## OWASP review

| OWASP area | Status | Current treatment or required action |
| --- | --- | --- |
| Use a framework session implementation | Implemented | Spring Security and Spring Session provide session handling; no custom ID generator or session protocol is used. Keep the dependencies current. |
| Opaque, unpredictable server-generated IDs | Implemented by framework | Spring Session generates opaque UUID-style session IDs. The browser receives no user data or authorization data in the cookie. Do not replace this generator with application code. |
| Use cookies only; reject URL session IDs | Implemented | `COOKIE` is the only configured servlet tracking mode. Exercise this with an integration test using a URL `;jsessionid=` value. |
| HTTPS for the whole authenticated session | Implemented in application configuration | TLS is configured and the production cookie is `Secure`. Verify redirects, TLS termination, and HSTS at the deployed edge; do not expose authenticated HTTP. |
| `HttpOnly`, `Secure`, and `SameSite` cookie attributes | Implemented | `HttpOnly=true`, production `Secure=true`, and `SameSite=Lax` are explicit. `Lax` is intentional because `Strict` can prevent the existing cross-site OIDC callback from sending the pre-login session cookie. SameSite complements, rather than replaces, CSRF protection. |
| Cookie name prefix | Improvement | OWASP recommends `__Host-` for host-only session cookies. Consider `__Host-id` only after confirming `Secure`, `Path=/`, no `Domain`, and OIDC/local-development behavior at the public edge. |
| Narrow cookie domain and path | Partial / deployment verification | The application does not set `Domain`, so the browser defaults to host-only scope. Confirm the effective `Path` and ensure unrelated applications do not share the production host. |
| Avoid persistent browser storage | Implemented | The session cookie has no configured persistence lifetime, and the application does not use browser storage for session secrets. |
| Rotate ID on authentication | Framework default; add regression test | Spring Security's default session-fixation protection changes the session ID on authentication unless overridden. This configuration does not override it. Add a test proving the old ID is invalid after OIDC login. |
| Rotate or terminate on privilege change | Not implemented | Role/group changes and user disablement do not currently define how existing sessions are invalidated or reauthenticated. Define and implement this before managing production users. |
| Idle timeout | Implemented | The 15-minute timeout is server-enforced. Confirm it is appropriate for the system's data sensitivity and user workflow. |
| Absolute timeout | Implemented | `AbsoluteSessionTimeoutFilter` invalidates a session once it reaches 12 hours, regardless of activity. |
| Session renewal timeout | Not implemented | Periodic ID renewal is not required for the current 15-minute idle-only model, but reconsider it if a long absolute session lifetime is introduced. |
| Logout and server-side invalidation | Partial | OIDC RP-initiated logout and Keycloak back-channel logout are configured. Provide a visible logout control in any browser UI and test server-side invalidation. |
| Browser cache and logout cleanup | Partial | Spring Security supplies restrictive cache-control headers for protected responses. `Clear-Site-Data` is not sent on logout; assess it when the application serves sensitive browser content. |
| Reauthentication after risk events | Product decision | Define reauthentication/MFA requirements for account recovery, suspicious activity, and sensitive profile or authorization changes with the identity-provider owner. |
| Concurrent sessions | Product decision | Define the permitted number of active sessions per account and what happens when the limit is reached. No concurrency policy is configured. |
| Session anomaly detection and lifecycle logging | Partial | Authentication and logout outcomes are logged without session IDs. Define privacy-preserving detection for unusual session activity and invalid-ID attempts; never log raw IDs, cookies, or tokens. |

## Required production decisions

Before production use, the service owner must record and implement decisions for:

1. The rationale for the 15-minute idle and 12-hour absolute timeouts.
2. Maximum concurrent sessions and the behavior when that limit is exceeded.
3. How user disablement, group/role changes, and other privilege changes revoke or
   refresh existing sessions.
4. Risk events requiring reauthentication or MFA, coordinated with Keycloak.
5. Database encryption, backups, retention, and access monitoring for session
   tables and their serialized attributes.
6. Whether `__Host-id` and `Clear-Site-Data` are appropriate after testing the
   deployed HTTPS and OIDC flows.

## Verification

Test the externally deployed service, not just the local profile, for the
following:

1. `Set-Cookie` has the intended name, `HttpOnly`, `Secure`, `SameSite=Lax`,
   host-only scope, and no persistence lifetime.
2. A URL-supplied session ID is not accepted.
3. The session ID changes after authentication and the previous ID cannot access
   protected resources.
4. The idle and 12-hour absolute timeouts invalidate the server-side session and the browser must
   authenticate again.
5. Local logout and Keycloak back-channel logout invalidate the session and
   remove access.
6. Login, logout, timeout, privilege-change, and concurrent-session behavior
   match the documented production decisions.

Related documentation: [Security authentication](security-authentication.md),
[HTTP security headers](security-headers.md),
[security logging](security-logging.md), and
[ADR 0005](adr/0005-jdbc-backed-server-side-sessions.md).
