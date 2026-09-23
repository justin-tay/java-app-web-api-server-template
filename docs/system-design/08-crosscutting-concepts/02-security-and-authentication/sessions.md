# Sessions

The session-management posture of the application is recorded here against
the [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
describing the application configuration; the production deployment must also
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
  [ADR 0004](../../../adr/0004-database-schema-management.md).

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

## OWASP control implementation

### Status meanings

| Status | Meaning |
| --- | --- |
| Implemented | The application's own code or configuration fulfills the recommendation. How, whether by explicit application configuration, unconfigured framework default behavior, or an automated integration test that would catch a regression, is explained in the same row. |
| Improvement | A stronger option exists beyond the current configuration; adopting it requires a deployment-specific decision. |
| Partial | The recommendation is only partly satisfied; the remainder, including any deployment-specific verification still required, is explained in the same row. |
| Not implemented | No mechanism currently satisfies the recommendation. |
| Not applicable | The template has no capability, use case, or browser-side surface the recommendation addresses. Reassess before introducing one. |
| Verify framework behavior | Satisfied by unconfigured Spring Security/Spring Session default behavior; cite the framework's documented behavior and recheck it after upgrades. |
| Deployment responsibility | The recommendation belongs to infrastructure (a WAF, the deployment edge) rather than application code. |
| Product decision required | The application cannot safely choose the behavior; it depends on a product/business decision, typically coordinated with the identity-provider owner. |

This section follows the [Session Management Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)'s
own subsection headings, in its own order, "Introduction" excepted since it
is motivational rather than actionable. Values already recorded in [Current
configuration](#current-configuration) above are cited by name rather than
repeated.

### Session ID Properties

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session ID Name Fingerprinting**<br>Give the session ID cookie a generic name that does not reveal the underlying framework or technology. | Implemented | The cookie name is `id`, not a framework default (`JSESSIONID`) that would fingerprint the stack.<br><br>**Application configuration:** `server.servlet.session.cookie.name`. |
| **Session ID Entropy**<br>Generate session IDs from a cryptographically secure source with at least 64 bits of entropy. | Implemented | `JdbcIndexedSessionRepository`'s default ID generator produces a random UUID (122 bits of entropy) per session; the application supplies no custom generator. |
| **Session ID Length**<br>Make the session ID long enough to carry that entropy, for example at least 16 hexadecimal characters for 64 bits. | Implemented | The generated ID is a UUID string; length is fixed by the Spring Session default generator, not application code. |
| **Session ID Content (or Value)**<br>Keep the session ID meaningless, with no user data or business logic encoded in it; hold all session data server-side. | Implemented | The cookie value is the opaque session ID alone; it carries no username, role, timestamp, or other structured/decodable data. `JdbcIndexedSessionRepository` and `DefaultCookieSerializer` are unmodified defaults. |

### Session Management Implementation

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Built-in Session Management Implementations**<br>Use the framework's own session management instead of building a custom one, and keep it patched. | Implemented | Spring Security and Spring Session JDBC (`spring-boot-starter-session-jdbc`) provide session handling; no custom ID generator or session protocol is used. Keep the dependencies current. See [Design and ownership](#design-and-ownership) above. |
| **Used vs. Accepted Session ID Exchange Mechanisms**<br>Accept the session ID only through the mechanism the application actually uses, such as a cookie, and reject any other. | Implemented | `COOKIE` is the only configured servlet tracking mode; URL-rewritten `;jsessionid=` session IDs are not accepted.<br><br>**Application configuration:** `server.servlet.session.tracking-modes: COOKIE` in `application.yaml`. Exercise this with an integration test using a URL `;jsessionid=` value. |
| **Transport Layer Security**<br>Protect the entire session with HTTPS, backed by the Secure cookie attribute and HSTS. | Implemented | TLS is configured and the production cookie is `Secure`. Verify redirects, TLS termination, and HSTS at the deployed edge; do not expose authenticated HTTP.<br><br>**Application configuration:** `server.ssl` and `server.servlet.session.cookie.secure`; **deployment:** edge verification. |

### Cookies

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Secure Attribute**<br>Set Secure on the session cookie so the browser only sends it over encrypted connections. | Implemented | Production `Secure=true`; `local`/`test` profiles set `secure: false` as an explicit development exception.<br><br>**Application configuration:** `server.servlet.session.cookie.secure`. |
| **HttpOnly Attribute**<br>Set HttpOnly on the session cookie so client-side scripts cannot read it. | Implemented | `HttpOnly=true`, preventing JavaScript from reading the cookie.<br><br>**Application configuration:** `server.servlet.session.cookie.http-only`. |
| **SameSite Attribute**<br>Set SameSite explicitly, preferably Strict or otherwise Lax, as defense in depth against cross-site requests. | Implemented | `SameSite=Lax` is explicit. `Lax`, not `Strict`, is intentional because `Strict` can prevent the existing cross-site OIDC callback from sending the pre-login session cookie. SameSite complements, rather than replaces, CSRF protection. Confirm `Set-Cookie` carries `SameSite=Lax` against the deployed service.<br><br>**Application configuration:** `server.servlet.session.cookie.same-site`. |
| **Cookie Name Prefixes**<br>Name the session cookie with the __Host- prefix so it must be Secure, host-only, and scoped to Path=/. | Improvement | OWASP recommends `__Host-` for host-only session cookies. Consider `__Host-id`, as a production decision, only after confirming `Secure`, `Path=/`, no `Domain`, and OIDC/local-development behavior at the public edge. |
| **Domain and Path Attributes**<br>Scope the cookie as narrowly as possible by omitting Domain and choosing a restrictive Path. | Partial | The application does not set `Domain`, so the browser defaults to host-only scope. Confirm the effective `Path` and ensure unrelated applications do not share the production host.<br><br>**Spring Session default:** host-only domain and context-root path; **deployment:** verify effective scope. |
| **Expire and Max-Age Attributes**<br>Use a non-persistent session cookie by setting neither Expires nor Max-Age. | Implemented | No `Max-Age` or `Expires` value is configured, so the cookie is non-persistent and does not survive browser close. Confirm against the deployed service that the cookie carries no persistence lifetime.<br><br>**Spring Session default:** session cookie has no `Max-Age`. |

### HTML5 Web Storage API

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **The localStorage API**<br>Do not store session identifiers or other secrets in localStorage, which any script on the origin can read. | Not applicable | The application is a JSON API with no browser-rendered UI of its own beyond Spring Security's generated login page; no application code writes a session ID, OAuth token, or credential to `localStorage`. Reassess if a browser UI is added. |
| **The sessionStorage API**<br>Do not store session identifiers or other secrets in sessionStorage, which is tab-scoped but still script-readable. | Not applicable | Same as `localStorage` above: no application code uses `sessionStorage` for security-relevant data. |

### Web Workers

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Web Workers**<br>Keep session secrets that don't need to survive a page refresh inside a Web Worker, run every operation that needs them there, and never pass them back to the main window.<br>**Caveat:** this matches an `HttpOnly` cookie only against theft; injected script can still message the worker to use the secret ([RFC 10017](https://datatracker.ietf.org/doc/html/rfc10017) sections 8.2, 8.3). | Not applicable | The application ships no browser-side JavaScript or Web Worker of its own. |

### Session ID Life Cycle

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session ID Generation and Verification: Permissive and Strict Session Management**<br>Use strict session management: accept only session IDs the application itself generated and reject any others. | Implemented | Spring Session's `JdbcIndexedSessionRepository` only accepts a presented session ID that matches a stored, non-expired JDBC row (strict verification); it does not create a session record for an unrecognized ID. |
| **Manage Session ID as Any Other User Input**<br>Validate a received session ID like any untrusted input before using it. | Verify framework behavior | The application never parses, decodes, or trusts structure inside the session ID; it is used only as an opaque lookup key by `JdbcIndexedSessionRepository`. No application code path branches on the ID's content. |
| **Renew the Session ID After Any Privilege Level Change**<br>Issue a new session ID on login and on every other privilege change, such as a role or permission change. | Implemented | Spring Security's default session-fixation protection changes the session ID on authentication unless overridden, and this configuration does not override it. Confirm against the deployed service that the previous session ID cannot access protected resources after rotation. `WebSecurityConfigurationSessionManagementIntegrationTest` proves a successful authentication replaces the JDBC session ID and removes the old row. Disabling or deleting a user, or changing their own group membership, additionally revokes their session immediately through `SessionRevocationService`; every other authorization-relevant change (redefining a group's role set, deleting a role) still takes effect on the affected user's very next request, since authorities are reloaded from the database on every request rather than trusted from the session.<br><br>**Application code:** `SessionRevocationService`, called from `AdministrationService`; `LocalAuthorityRefreshFilter`. |
| **Reauthentication After Risk Events**<br>Require the user to reauthenticate after high-risk events such as credential changes, suspicious logins, or account recovery. | Product decision required | See "Reauthentication After Risk Events" below; the cheat sheet lists this recommendation twice. |
| **Considerations When Using Multiple Cookies**<br>When a session spans several cookies, verify all of them and the relationship between them before granting access. | Not applicable | The application sets exactly one session-related cookie (`id`); there is no second, related cookie to coordinate lifecycle with. |

### Session Expiration

#### Automatic Session Expiration

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Idle Timeout**<br>Expire a session server-side after a period of inactivity. | Implemented | The 15-minute timeout is server-enforced. Confirm it is appropriate for the system's data sensitivity and user workflow, and that the deployed service invalidates the session and requires re-authentication once it elapses.<br><br>**Application configuration:** `server.servlet.session.timeout: 15m`. |
| **Absolute Timeout**<br>Expire a session server-side after a maximum lifetime, regardless of activity. | Implemented | `AbsoluteSessionTimeoutFilter` invalidates a session once it reaches 12 hours, regardless of activity. Confirm against the deployed service that the session is invalidated and the browser must authenticate again.<br><br>**Application code:** `AbsoluteSessionTimeoutFilter`, before `SecurityContextHolderFilter`, uses `app.session.absolute-timeout: 12h` and the shared `Clock`. |
| **Renewal Timeout**<br>Optionally regenerate the session ID periodically during a session, independent of activity. | Not implemented | Periodic ID renewal is not required for the current 15-minute idle-only model, but reconsider it if a long absolute session lifetime is introduced. |

#### Manual Session Expiration

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Logout Button**<br>Offer a visible logout control on every page that invalidates the session server-side. | Partial | OIDC RP-initiated logout and Keycloak back-channel logout are configured (`OidcClientInitiatedLogoutSuccessHandler`, `http.oidcLogout(...)`; see [Logout](authentication.md#logout) in [Authentication](authentication.md)). Provide a visible logout control in any browser UI. Confirm both local logout and Keycloak back-channel logout invalidate the JDBC session row.<br><br>**Application configuration:** `WebSecurityConfiguration` configures OIDC logout handlers; UI and integration coverage remain required. |

#### Web Content Caching and Clear-Site-Data

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Web Content Caching and Clear-Site-Data**<br>Prevent sensitive responses from being cached and use Clear-Site-Data on logout to clear browser-held data. | Partial | Spring Security supplies restrictive cache-control headers for protected responses (see [HTTP security headers](headers.md)). `Clear-Site-Data` is not sent on logout; assess it when the application serves sensitive browser content.<br><br>**Spring Security default:** protected-response cache headers; **unimplemented:** `Clear-Site-Data` logout handler. |

### Reauthentication After Risk Events

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Reauthentication After Risk Events**<br>Require reauthentication, with primary credentials or MFA, after events such as password changes, logins from new devices, or account recovery. | Product decision required | Define reauthentication/MFA requirements for account recovery, suspicious activity, and sensitive profile or authorization changes with the identity-provider owner. This mirrors the equivalent open item in [Authentication](authentication.md#authentication-general-guidelines). |

### Additional Client-Side Defenses for Session Management

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Initial Login Timeout**<br>Use client-side code to reload the login page, and so obtain a fresh session ID, if login is not completed within a set time. | Not applicable | Login is Keycloak's hosted page, outside this application's rendering; the application defines no client-side login-form timeout. |
| **Force Session Logout On Web Browser Window Close Events**<br>Use client-side code to log the user out when the browser window or tab is closed. | Not applicable | The session cookie already has no `Max-Age` (see Expire and Max-Age Attributes above); the application renders no browser UI to attach a window-close handler to. |
| **Disable Web Browser Cross-Tab Sessions**<br>Use client-side code to require reauthentication when the application is opened in a new tab or window. | Not applicable | The application has no browser-side session code; this is a client-side UI concern the template does not own. |
| **Automatic Client Logout**<br>Use client-side code to log the user out and warn them once the idle timeout is reached. | Not applicable | No application-rendered UI exists to run an inactivity timer against; server-side idle/absolute timeouts (above) are the enforced control. |

### Session Attacks Detection

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session ID Guessing and Brute Force Detection**<br>Detect repeated requests with different session IDs from the same source, then alert on or block it. | Not implemented | No application code counts or rate-limits failed session-ID lookups; `JdbcIndexedSessionRepository` simply returns no session for an unrecognized ID. Define detection thresholds and alert routing before adding this. |
| **Detecting Session ID Anomalies**<br>Detect tampering with the session ID and unexpected changes within a session, for example with OWASP AppSensor. | Partial | Lifecycle events are logged for audit-ID initialization, session-fixation renewal, logout, absolute timeout, and concurrent-session expiry, but no anomaly-detection rule (unexpected IP/user-agent change, impossible travel) runs over them. JDBC cleanup of an idle session and arbitrary invalid-cookie attempts are not inferred or logged, because the application has no reliable hook for either.<br><br>**Application code:** `SessionLifecycleAuditLogger`. |
| **Binding the Session ID to Other User Properties**<br>Tie the session ID to client properties such as IP address or User-Agent to help detect hijacking. | Not implemented | The session is not bound to the originating IP address or User-Agent; a change in either does not invalidate the session today. Do not add this as an automatic invalidation criterion without a product decision (see [Required production decisions](#required-production-decisions) below). |
| **Logging Sessions Life Cycle: Monitoring Creation, Usage, and Destruction of Session IDs**<br>Log session creation, renewal, use, and destruction, recording a hash of the session ID rather than the raw value. | Implemented for known causes | `SessionLifecycleAuditLogger`, `SessionLifecycleAuditInitializationFilter`, `AbsoluteSessionTimeoutFilter`, and `SessionLifecycleLogoutHandler` log creation, session-fixation renewal, absolute timeout, and logout. The event's `session.id` is a random application-local identifier, never the cookie or raw Spring Session ID. Confirm against the deployed service that these lifecycle logs correlate via that separate audit `session.id` and never contain a cookie or raw Spring Session ID. See [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md). |
| **Simultaneous Session Logons**<br>Decide whether to allow concurrent sessions per user, and let users see and end their active sessions. | Implemented | One concurrent session is permitted per user. A later successful login marks the existing session expired; its next request invalidates it. Browser navigation redirects to `/login?session-expired`; API requests receive a generic 401 Problem Details response.<br><br>**Application configuration:** `maximumSessions(1)` with `maxSessionsPreventsLogin(false)` and `ContentNegotiatingSessionExpiredStrategy`; **Spring Session:** `SpringSessionBackedSessionRegistry` finds sessions in JDBC across instances. **Override rationale:** Spring Security's default `ConcurrentSessionFilter` writes a plain-text expiry message without setting a status, leaving HTTP 200; this is unsuitable for browser navigation and API clients. `WebSecurityConfigurationSessionManagementIntegrationTest` verifies both response types and JDBC-session invalidation. |

### Session Management WAF Protections

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session Management WAF Protections**<br>Use a web application firewall to enforce session protections when the application code itself cannot be changed. | Deployment responsibility | A web application firewall is infrastructure the application cannot configure from within; deploying one, and tuning session-related rules (ID-format anomalies, cookie tampering), is an edge/deployment decision. |

## Required production decisions

Before production use, the service owner must record and implement decisions
for the following, then confirm against the deployed service that login,
logout, timeout, privilege-change, and concurrent-session behavior actually
match what is decided:

1. The rationale for the 15-minute idle and 12-hour absolute timeouts.
2. Whether redefining a group's role set, or deleting a role, should also revoke
   the sessions of every member affected, and how any other privilege change not
   already covered by `SessionRevocationService` should revoke or refresh existing
   sessions.
3. Risk events requiring reauthentication or MFA, coordinated with Keycloak.
4. Database encryption, backups, retention, and access monitoring for session
   tables and their serialized attributes.
5. Whether `__Host-id` and `Clear-Site-Data` are appropriate after testing the
   deployed HTTPS and OIDC flows.
6. Whether suspicious IP/user-agent changes, invalid-session attempts, and
   session-ID guessing are monitored, including trusted data sources, thresholds,
   alert routing, and privacy/retention controls. Do not use IP or user-agent
   changes as automatic invalidation criteria without a product decision.

Related documentation: [Authentication](authentication.md),
[HTTP security headers](headers.md), [Logging](../06-logging-and-monitoring/README.md), and
[ADR 0006](../../../adr/0006-jdbc-backed-server-side-sessions.md).
