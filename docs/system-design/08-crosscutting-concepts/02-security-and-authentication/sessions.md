# Sessions

The session-management posture of the application is recorded here against
the [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
describing the application configuration; the production deployment must also
verify the effective cookie and HTTPS headers at its public edge.

## Design and ownership

Spring Security uses the servlet `HttpSession` for the security context, OAuth2
authorization request, and saved request. Spring Session replaces that servlet
session with JDBC-backed storage:

* The browser holds only the opaque `__Host-id` session cookie (`id` in the
  plain-HTTP `local` and `test` profiles).
* Session metadata is stored in `SPRING_SESSION`.
* Serialized session attributes are stored in `SPRING_SESSION_ATTRIBUTES`.
* Spring Boot auto-configures Spring Session JDBC through
  `spring-boot-starter-session-jdbc`. Liquibase owns those tables, and Spring
  Boot's JDBC session-schema initializer is disabled. See
  [ADR 0004](../../../adr/0004-database-schema-management.md).

Two pieces of login state are held in memory on the instance that handled the
login, not in the session, so a restart discards them and other instances
cannot see them:

* **OAuth2 authorized clients** (the Keycloak access token `/account` uses):
  Spring Boot's default `InMemoryOAuth2AuthorizedClientService`, reached
  through `AuthenticatedPrincipalOAuth2AuthorizedClientRepository`, stores
  them per authenticated principal. A request for the access token on another
  instance, or after a restart, finds none and must re-authorize.
* **The OIDC session registry** that links a Keycloak session to the local
  session for back-channel logout (`InMemoryOidcSessionRegistry`), so
  back-channel logout is reliable only with a single instance; see
  [Back-channel logout](authentication.md#back-channel-logout).

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

Every value above is a commons default (`commons-defaults.yaml` and
`WebSecurityAutoConfiguration`), so each `app-*` module inherits it; the
`local` and `test` profiles override only `Secure`. The Liquibase changeset ships
in commons at the same classpath path, and each application's master
changelog includes it.

The cookie is non-persistent because no `Max-Age` or `Expires` value is
configured. No session ID, OAuth token, or credential is deliberately stored in
browser `localStorage` or `sessionStorage`.

## OWASP control implementation

The application's session-management posture is recorded here against the
[OWASP Session Management Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

### Status meanings

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Status | Meaning |
| --- | --- |
| Implemented | The application's own code or configuration fulfils the recommendation. |
| Inherited from framework | Met by an unmodified framework or library default. A test that would catch a regression on upgrade is cited where one exists; otherwise the framework's documented behaviour is cited. |
| Partial | Part of the recommendation is met; the row says which part and why the rest is not. |
| Alternative | A deliberate, documented approach is used instead of the recommended one; the row says what it gives up compared with the recommendation, so an adopter can decide whether that is acceptable for their system. |
| Not implemented | Relevant, and nothing in the application meets it yet: a gap. A pending decision is this status, named in the row. |
| Not applicable | The application has no capability or mechanism the recommendation addresses. Reassess before adding one. |
| Deployment responsibility | Belongs to whoever deploys the application: infrastructure, runtime, or an operational choice. |
<!-- /ocsv:generated -->

### Session ID Properties

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session ID Name Fingerprinting**<br>Give the session ID cookie a generic name that does not reveal the underlying framework or technology. | Implemented | The cookie name is `id`, not a framework default (`JSESSIONID`) that would fingerprint the stack.<br><br>**Application configuration:** `server.servlet.session.cookie.name` in `commons-defaults.yaml`; **Test code:** `WebSecurityConfigurationTest.unauthenticatedRequestCreatesIdSessionCookie()`. |
| **Session ID Entropy**<br>Generate session IDs from a cryptographically secure source with at least 64 bits of entropy. | Implemented | `SecureRandomSessionIdGenerator` draws 256 random bits from `SecureRandom` for each session ID, in place of Spring Session's version 4 UUIDs, which carry 122.<br><br>**Application code:** `SecureRandomSessionIdGenerator`, `WebSecurityAutoConfiguration.sessionIdGenerator()`. |
| **Session ID Length**<br>Make the session ID long enough to carry that entropy, for example at least 16 hexadecimal characters for 64 bits. | Implemented | The ID is 43 characters of base64url carrying 256 random bits; `DefaultCookieSerializer` Base64-encodes it again in the cookie. The `SESSION_ID` column is `VARCHAR(64)` to hold it.<br><br>**Application code:** `SecureRandomSessionIdGenerator`; **Application configuration:** `003-spring-session-schema.sql`. |
| **Session ID Content (or Value)**<br>Keep the session ID meaningless, with no user data or business logic encoded in it; hold all session data server-side. | Inherited from framework | **Session ID:** the cookie value is the opaque session ID alone; it carries no username, role, timestamp, or other structured or decodable data. `JdbcIndexedSessionRepository` and `DefaultCookieSerializer` are unmodified defaults, and the ID is the one the framework creates.<br><br>**Session repository:** session data is held server-side in `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES`. Those rows hold the Spring Security context and OAuth2 login state, so protecting the database (access, encryption at rest, backups) belongs to the deployment; see [Design and ownership](#design-and-ownership) and item 4 of [Required production decisions](#required-production-decisions).<br><br>**Framework default:** Spring Session `JdbcIndexedSessionRepository`, `DefaultCookieSerializer`; **Deployment:** session-table protection. |
<!-- /ocsv:generated -->

### Session Management Implementation

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Built-in Session Management Implementations**<br>Use the framework's own session management instead of building a custom one, keep it patched, and protect its session store. | Partial | **Built-in implementation:** Spring Security and Spring Session JDBC (`spring-boot-starter-session-jdbc`) provide session handling; no custom ID generator or session protocol is used. The defaults are reviewed and changed where the Cheat Sheet asks (cookie name, tracking mode, `SameSite`, idle and absolute timeouts). See [Design and ownership](#design-and-ownership) above.<br><br>**Latest version:** not implemented. The Spring Boot parent version is pinned in `pom.xml`, and neither the build nor the CI workflow (`build-and-test.yml`) automates dependency updates or runs a vulnerability scan; keep the dependencies current by hand.<br><br>**Session store:** the JDBC store's protection is a deployment concern (see Session ID Content (or Value) above).<br><br>**Application configuration:** `spring-boot-starter-session-jdbc` in `commons/pom.xml`, `server.servlet.session.*` in `commons-defaults.yaml`; **Decision:** [ADR 0006](../../../adr/0006-jdbc-backed-server-side-sessions.md). |
| **Used vs. Accepted Session ID Exchange Mechanisms**<br>Accept the session ID only through the mechanism the application actually uses, such as a cookie, and reject any other. | Implemented | **Cookie only:** `COOKIE` is the only configured servlet tracking mode, and Spring Session resolves the session only from the session cookie, so URL-rewritten `;jsessionid=` session IDs are not accepted.<br><br>**Testing:** an integration test logs in through OIDC and then presents the authenticated session ID every other way, without the cookie: as `;jsessionid=` and `;id=` path parameters (rejected with 400 by Spring Security's HTTP firewall), as `?jsessionid=` and `?id=` query parameters, in `X-Auth-Token` and `SESSION` headers, and in `SESSION` and `JSESSIONID` cookies (each answered 401). None is authenticated, and the session's last-access time is unchanged, so none resumed it; the same ID in the `id` cookie then authenticates and resumes it.<br><br>**Application configuration:** `server.servlet.session.tracking-modes: COOKIE` in `commons-defaults.yaml`; **Framework default:** Spring Session `CookieHttpSessionIdResolver`; **Test code:** `SessionIdExchangeIntegrationTest.onlyTheIdCookieResumesTheSession()`. |
| **Transport Layer Security**<br>Protect the entire session with HTTPS, backed by the Secure cookie attribute and HSTS. | Implemented | TLS is configured (TLS 1.2 and 1.3 only) on the application's server port, which has no plain-HTTP connector, so a session is never switched between HTTP and HTTPS, and the production cookie is `Secure`. The application serves no mixed or public unencrypted content. Spring Security's default HSTS header is emitted only for requests the application sees as secure (see [HTTP security headers](headers.md)). The `local` and `test` profiles disable TLS as a development exception. Verify redirects, TLS termination, and HSTS at the deployed edge; do not expose authenticated HTTP.<br><br>**Application configuration:** `server.ssl` and `server.servlet.session.cookie.secure` in `commons-defaults.yaml`; **Framework default:** Spring Security `HstsHeaderWriter`; **Deployment:** edge verification. |
<!-- /ocsv:generated -->

### Cookies

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Secure Attribute**<br>Set Secure on the session cookie so the browser only sends it over encrypted connections. | Implemented | Production `Secure=true`; the `local` and `test` profiles set `secure: false` as an explicit development exception, so no automated test asserts the production attribute.<br><br>**Application configuration:** `server.servlet.session.cookie.secure` in `commons-defaults.yaml`, overridden in `application-local.yaml` and `application-test.yaml`. |
| **HttpOnly Attribute**<br>Set HttpOnly on the session cookie so client-side scripts cannot read it. | Implemented | `HttpOnly=true`, preventing JavaScript from reading the cookie.<br><br>**Application configuration:** `server.servlet.session.cookie.http-only` in `commons-defaults.yaml`; **Test code:** `WebSecurityConfigurationTest.idSessionCookieIsNonPersistentHttpOnlyAndSameSiteLax()`. |
| **SameSite Attribute**<br>Set SameSite explicitly, preferably Strict or otherwise Lax, as defense in depth against cross-site requests. | Implemented | `SameSite=Lax` is explicit. `Lax`, not `Strict`, is intentional because `Strict` can prevent the existing cross-site OIDC callback from sending the pre-login session cookie. SameSite complements, rather than replaces, CSRF protection, which stays enabled. Confirm `Set-Cookie` carries `SameSite=Lax` against the deployed service.<br><br>**Application configuration:** `server.servlet.session.cookie.same-site` in `commons-defaults.yaml`; **Test code:** `WebSecurityConfigurationTest.idSessionCookieIsNonPersistentHttpOnlyAndSameSiteLax()`, `WebSecurityConfigurationTest.csrfFailureReturnsActionableProblemDetail()`. |
| **Cookie Name Prefixes**<br>Name the session cookie with the __Host- prefix so it must be Secure, host-only, and scoped to Path=/. | Implemented | The session cookie is named `__Host-id`, so the browser accepts it only when it is `Secure`, has `Path=/`, and has no `Domain`, and no other host or path can set or shadow it. The plain-HTTP `local` and `test` profiles name it `id`, because a `__Host-` cookie must be `Secure`. The prefix needs the application at the root context path.<br><br>**Application configuration:** `server.servlet.session.cookie.name` in `commons-defaults.yaml`, overridden in `application-local.yaml` and `application-test.yaml`. |
| **Domain and Path Attributes**<br>Scope the cookie as narrowly as possible by omitting Domain and choosing a restrictive Path. | Inherited from framework | The application sets neither attribute. `DefaultCookieSerializer` omits `Domain`, so the browser defaults to host-only scope, and sets `Path` to the context path plus `/`, which is `/` because no context path is configured. Keeping unrelated applications, especially of other security levels, off the production host and domain belongs to the deployment. Confirm the effective `Path` and ensure unrelated applications do not share the production host.<br><br>**Framework default:** Spring Session `DefaultCookieSerializer`; **Deployment:** verify effective scope. |
| **Expire and Max-Age Attributes**<br>Use a non-persistent session cookie by setting neither Expires nor Max-Age. | Inherited from framework | **Non-persistent cookie:** no `Max-Age` or `Expires` value is configured, so the cookie is non-persistent and does not survive browser close. Confirm against the deployed service that the cookie carries no persistence lifetime.<br><br>**Cookie handling:** the cookie holds only the opaque session ID, so nothing sensitive is persisted or needs encrypting, and manipulating it can only select a different stored session. The `Secure` flag is set (see Secure Attribute above), every request outside the explicit public paths requires an authenticated session, and `id` is the only cookie the application sets (see [Current configuration](#current-configuration)).<br><br>**Framework default:** Spring Session `DefaultCookieSerializer` sends no `Max-Age`; **Test code:** `WebSecurityConfigurationTest.idSessionCookieIsNonPersistentHttpOnlyAndSameSiteLax()`. |
<!-- /ocsv:generated -->

### HTML5 Web Storage API

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **The localStorage API**<br>Do not store session identifiers or other secrets in localStorage, which any script on the origin can read. | Not applicable | The application is a JSON API with no browser-rendered UI of its own beyond Spring Security's generated login and logout pages; no application code writes a session ID, OAuth token, or credential to `localStorage`. The session ID stays in an `HttpOnly` cookie and OAuth2 state stays server-side. Reassess if a browser UI is added. |
| **The sessionStorage API**<br>Do not store session identifiers or other secrets in sessionStorage, which is tab-scoped but still script-readable. | Not applicable | As for `localStorage` above: the application ships no browser-side code, and no application code uses `sessionStorage` for security-relevant data. Reassess if a browser UI is added. |
<!-- /ocsv:generated -->

### Web Workers

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Web Workers**<br>Keep session secrets that don't need to survive a page refresh inside a Web Worker, run every operation that needs them there, and never pass them back to the main window.<br>**Caveat:** this matches an `HttpOnly` cookie only against theft; injected script can still message the worker to use the secret ([RFC 10017](https://datatracker.ietf.org/doc/html/rfc10017) sections 8.2, 8.3). | Not applicable | The application ships no browser-side JavaScript or Web Worker of its own. |
<!-- /ocsv:generated -->

### Session ID Life Cycle

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session ID Generation and Verification: Permissive and Strict Session Management**<br>Use strict session management: accept only session IDs the application itself generated, issue a new one in place of any other, and alert on it. | Partial | **Strict verification:** Spring Session's `JdbcIndexedSessionRepository` only accepts a presented session ID that matches a stored, non-expired JDBC row; it does not create a session record for an unrecognized ID. When a session is needed, `SessionRepositoryFilter` creates one under a newly generated ID.<br><br>**Alerting:** not implemented. A presented ID that is not found is logged by `AuditingInvalidSessionStrategy` as a `resume_session` failure, without the ID; the application cannot tell an ID it never generated from one that expired or ended, and nothing alerts on these events (see Session ID Guessing and Brute Force Detection below).<br><br>**Framework default:** Spring Session `JdbcIndexedSessionRepository`, `SessionRepositoryFilter`; **Application code:** `AuditingInvalidSessionStrategy`. |
| **Manage Session ID as Any Other User Input**<br>Validate a received session ID like any untrusted input before using it. | Inherited from framework | The application never parses, decodes, or trusts structure inside the session ID; `DefaultCookieSerializer` decodes the cookie and `JdbcIndexedSessionRepository` uses the result only as an opaque lookup key bound as a parameter in its SQL queries. No application code path branches on the ID's content, and the ID is never logged or reflected; lifecycle logs carry a separate audit identifier instead.<br><br>**Framework default:** Spring Session `DefaultCookieSerializer`, `JdbcIndexedSessionRepository`. |
| **Renew the Session ID After Any Privilege Level Change**<br>Issue a new session ID on login and on every other privilege change, such as a role or permission change. | Partial | **On login:** Spring Security's default session-fixation protection changes the session ID on authentication unless overridden, and this configuration does not override it. Confirm against the deployed service that the previous session ID cannot access protected resources after rotation. `WebSecurityConfigurationSessionManagementIntegrationTest.successfulAuthenticationRotatesTheSessionId()` proves, through a test-only form-login chain with the same session-management settings, that a successful authentication replaces the JDBC session ID and removes the old row.<br><br>**Other privilege changes:** disabling or deleting a user, or changing their own group membership, revokes their session immediately through `SessionRevocationService`, so they must sign in again under a new ID. Every other authorization-relevant change (redefining a group's role set, deleting a role) takes effect on the affected user's very next request, since authorities are reloaded from the database on every request rather than trusted from the session, but it neither renews nor revokes the session ID. Whether those changes should also revoke sessions is item 2 of [Required production decisions](#required-production-decisions).<br><br>**Framework default:** Spring Security session-fixation protection (`ChangeSessionIdAuthenticationStrategy`); **Application code:** `SessionRevocationService`, called from `AdministrationService`; `LocalAuthorityRefreshFilter`; **Test code:** `WebSecurityConfigurationSessionManagementIntegrationTest.successfulAuthenticationRotatesTheSessionId()`, `AdministrationServiceTest`, `SessionRevocationServiceTest`, `LocalAuthorityRefreshFilterTest`. |
| **Reauthentication After Risk Events**<br>Require the user to reauthenticate after high-risk events such as credential changes, suspicious logins, or account recovery. | Not implemented | No application mechanism requires reauthentication after a risk event. Password changes and account recovery happen in Keycloak, outside the application; the application receives no new-device or suspicious-IP signal; and an administrator changing a user's email address through the administration API neither revokes the session nor requires reauthentication. Which risk events require reauthentication or MFA is item 3 of [Required production decisions](#required-production-decisions); the Cheat Sheet's top-level section of the same name is mapped below. |
| **Considerations When Using Multiple Cookies**<br>When a session spans several cookies, verify all of them and the relationship between them before granting access. | Not applicable | The application sets exactly one session-related cookie (`id`), before and after authentication; there is no second, related cookie to coordinate lifecycle with, and no cookie name is reused across paths or domains. |
<!-- /ocsv:generated -->

### Session Expiration

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session Expiration**<br>Set an expiration timeout for every session, and when a session expires or the user logs out, invalidate it on the server (mandatory) and clear it on the client. | Implemented | **Timeouts:** every session has a 15-minute idle timeout and a 12-hour absolute timeout (see the rows below). Confirm the values suit the system's data sensitivity and workflow; their rationale is item 1 of [Required production decisions](#required-production-decisions).<br><br>**Invalidation:** the server invalidates an expired or logged-out session: Spring Session treats a session past its idle timeout as absent, `AbsoluteSessionTimeoutFilter` calls `HttpSession.invalidate()`, and logout invalidates the session through Spring Security's logout handlers. When a session is invalidated during a request, Spring Session's `SessionRepositoryFilter` expires the `id` cookie on the client, unless the same request creates a new session, whose ID then replaces it.<br><br>**Application configuration:** `server.servlet.session.timeout` and `commons.security.session.absolute-timeout` in `commons-defaults.yaml`; **Application code:** `AbsoluteSessionTimeoutFilter`; **Framework default:** Spring Session `SessionRepositoryFilter`, Spring Security `SecurityContextLogoutHandler`; **Test code:** `AbsoluteSessionTimeoutIntegrationTest.expiredJdbcSessionIsRemovedBeforeProtectedRequestIsAuthorized()`. |
<!-- /ocsv:generated -->

#### Automatic Session Expiration

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Idle Timeout**<br>Expire a session server-side after a period of inactivity. | Implemented | The 15-minute timeout is server-enforced: Spring Boot applies it as Spring Session's maximum inactive interval, and the client holds no timing data. Confirm it is appropriate for the system's data sensitivity and user workflow, and that the deployed service invalidates the session and requires re-authentication once it elapses.<br><br>**Application configuration:** `server.servlet.session.timeout: 15m` in `commons-defaults.yaml`. |
| **Absolute Timeout**<br>Expire a session server-side after a maximum lifetime, regardless of activity. | Implemented | `AbsoluteSessionTimeoutFilter` invalidates a session once it reaches 12 hours from creation, regardless of activity, before any authorization decision in the security filter chain. Confirm against the deployed service that the session is invalidated and the browser must authenticate again.<br><br>**Application code:** `AbsoluteSessionTimeoutFilter`, registered in `WebSecurityAutoConfiguration.securityFilterChainCustomizer()` with the application's `Clock`; **Application configuration:** `commons.security.session.absolute-timeout: 12h` in `commons-defaults.yaml`; **Test code:** `AbsoluteSessionTimeoutFilterTest`, `AbsoluteSessionTimeoutIntegrationTest.expiredJdbcSessionIsRemovedBeforeProtectedRequestIsAuthorized()`. |
| **Renewal Timeout**<br>Optional: regenerate the session ID periodically during a session, independent of activity. | Not implemented | The session ID is not renewed periodically during a session. Renewal is optional, and not required for the current 15-minute idle timeout, but reconsider it if a long absolute session lifetime is introduced. |
<!-- /ocsv:generated -->

#### Manual Session Expiration

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Logout Button**<br>Offer a visible logout control on every page that invalidates the session server-side. | Partial | **Server-side invalidation:** Spring Security's logout invalidates the session, `SessionLifecycleLogoutHandler` records it, and OIDC RP-initiated logout is configured (`OidcClientInitiatedLogoutSuccessHandler`). Keycloak back-channel logout (`http.oidcLogout(...)`) uses `SessionRepositoryOidcBackChannelLogoutHandler` instead of Spring Security's default handler, which cannot resume a session through the `id` cookie: it deletes each session linked to the logout token from the JDBC repository and logs it with `session.termination_reason` `back_channel_logout`. The link between the Keycloak session and the local session is held in an in-memory `OidcSessionRegistry`, so back-channel logout is reliable only with a single instance; a notification that reaches another instance ends nothing (see [Back-channel logout](authentication.md#back-channel-logout)). Confirm against the deployed service that local logout invalidates the JDBC session row.<br><br>**Visible control:** not implemented. The application renders no pages of its own beyond Spring Security's generated login and logout pages, so there is no header or menu to carry a logout control; provide one in any browser UI.<br><br>**Application code:** `WebSecurityAutoConfiguration.securityFilterChainCustomizer()` (`logout(...)` handler), `OidcLoginSecurityAutoConfiguration.oidcLoginFilterChainCustomizer()` (`oidcLogout(...)`, `logout(...)` success handler), `OidcLoginSecurityAutoConfiguration.oidcSessionRegistry()`, `SessionLifecycleLogoutHandler`, `SessionRepositoryOidcBackChannelLogoutHandler`; **Framework default:** Spring Security `SecurityContextLogoutHandler`, `OidcBackChannelLogoutFilter`, `InMemoryOidcSessionRegistry`; **Test code:** `OidcBackChannelLogoutIntegrationTest.backChannelLogoutDeletesTheSessionEstablishedByOidcLogin()`, `SessionRepositoryOidcBackChannelLogoutHandlerTest`. |
<!-- /ocsv:generated -->

#### Web Content Caching and Clear-Site-Data

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Web Content Caching and Clear-Site-Data**<br>Prevent sensitive responses from being cached and use Clear-Site-Data on logout to clear browser-held data. | Partial | **Caching:** Spring Security sends `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache`, and `Expires: 0` on responses, including those that set the session cookie (see [HTTP security headers](headers.md)).<br><br>**Clear-Site-Data:** not implemented. `Clear-Site-Data` is not sent on logout or session termination; assess it when the application serves sensitive browser content. Whether to adopt it is item 5 of [Required production decisions](#required-production-decisions).<br><br>**Framework default:** Spring Security `CacheControlHeadersWriter`; **Test code:** `WebSecurityConfigurationTest.responseHasSpringSecurityDefaultHeaders()`. |
<!-- /ocsv:generated -->

### Reauthentication After Risk Events

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Reauthentication After Risk Events**<br>Require reauthentication, with primary credentials or MFA and a clear explanation to the user, after events such as password changes, logins from new devices, or account recovery. | Not implemented | The application prompts for no reauthentication or MFA after a risk event; password changes and account recovery are Keycloak flows, and the application receives no risk signal to act on. Define reauthentication/MFA requirements for account recovery, suspicious activity, and sensitive profile or authorization changes with the identity-provider owner; this is item 3 of [Required production decisions](#required-production-decisions). This mirrors the equivalent open item in [Authentication](authentication.md#authentication-general-guidelines). |
<!-- /ocsv:generated -->

### Additional Client-Side Defenses for Session Management

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Initial Login Timeout**<br>Use client-side code to reload the login page, and so obtain a fresh session ID, if login is not completed within a set time. | Not applicable | Credentials are entered on Keycloak's hosted page, outside this application's rendering; the only application-rendered login page is Spring Security's generated page, which runs no script (the application's `Content-Security-Policy` is `default-src 'none'`). The application defines no client-side login-form timeout. |
| **Force Session Logout On Web Browser Window Close Events**<br>Use client-side code to log the user out when the browser window or tab is closed. | Not applicable | The session cookie already has no `Max-Age` (see Expire and Max-Age Attributes above); the application renders no browser UI to attach a window-close handler to. |
| **Disable Web Browser Cross-Tab Sessions**<br>Use client-side code to require reauthentication when the application is opened in a new tab or window. | Not applicable | The application has no browser-side session code; this is a client-side UI concern the template does not own. The Cheat Sheet also notes it cannot work when, as here, the session ID travels in a cookie shared by every tab. |
| **Automatic Client Logout**<br>Use client-side code to log the user out and warn them once the idle timeout is reached. | Not applicable | No application-rendered UI exists to run an inactivity timer against; server-side idle/absolute timeouts (above) are the enforced control. |
<!-- /ocsv:generated -->

### Session Attacks Detection

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session ID Guessing and Brute Force Detection**<br>Detect repeated requests with different session IDs from the same source, then alert on or block it. | Not implemented | No application code counts or rate-limits failed session-ID lookups. Each request carrying a session ID that is not found is logged as a `resume_session` failure with `source.ip` (see the logging row below), which a detection system can count, but the application itself neither correlates these nor alerts or blocks. Define detection thresholds and alert routing before adding this; monitoring is item 6 of [Required production decisions](#required-production-decisions). |
| **Detecting Session ID Anomalies**<br>Detect tampering with the session ID and unexpected changes within a session, for example with OWASP AppSensor. | Not implemented | No anomaly-detection rule (modified or deleted cookie, reuse of another user's session ID, unexpected IP/user-agent change, impossible travel) runs in the application. Lifecycle events are logged for audit-ID initialization, session-fixation renewal, privilege change, logout, back-channel logout, absolute timeout, concurrent-session expiry, administrative revocation, and requests carrying a session ID that is not found (see the logging row below) and can feed such detection, but nothing consumes them for it. JDBC cleanup of an idle session that is never used again is not logged. Monitoring is item 6 of [Required production decisions](#required-production-decisions). |
| **Binding the Session ID to Other User Properties**<br>Tie the session ID to client properties such as IP address or User-Agent to help detect hijacking. | Not implemented | The session is not bound to the originating IP address or User-Agent; a change in either does not invalidate the session today. By default the application also resolves no trusted end-user client IP (`LoggingAutoConfiguration.clientIpResolver()` returns `ClientIpResolver.none()`) until a deployment supplies one. Do not add this as an automatic invalidation criterion without a product decision (see [Required production decisions](#required-production-decisions) below). |
| **Logging Sessions Life Cycle: Monitoring Creation, Usage, and Destruction of Session IDs**<br>Log session creation, renewal, use, and destruction, recording a hash of the session ID rather than the raw value. | Alternative | **Session identifier:** instead of the recommended salted hash of the session ID, each event's `session.id` is a random application-local audit identifier, never the cookie or raw Spring Session ID. This gives the same correlation across one session's events without anything derived from the session ID in the logs; what it gives up is matching a log entry to a presented session ID, for example to confirm which cookie an attacker replayed. Confirm against the deployed service that these lifecycle logs correlate via that separate audit `session.id` and never contain a cookie or raw Spring Session ID. See [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md).<br><br>**Logged:** creation (`create_session`, when the audit identifier is established), session-fixation renewal (`renew_session`), privilege change (`update_session` with reason `privilege_change`, the user, and the added and removed role names, when reloaded authorities differ from the session's), destruction (`destroy_session` with a `session.termination_reason`: logout, back-channel logout, absolute timeout, concurrent-session expiry, administrative revocation, or a disabled or deleted user), and invalid session activity (`resume_session` failure with reason `session_not_found` for a request carrying a session ID that is not found). Idle expiry is logged when detected on the session's next use, as that `resume_session` failure: the application cannot tell an idle-expired ID from an ended or forged one, so the event does not claim which. An idle session that is never used again is removed by JDBC cleanup without an event. Login and logout are also logged by `SecurityAuditEventLogger`. The application has no administrative interface for active sessions to protect.<br><br>**Application code:** `SessionLifecycleAuditLogger`, `SessionLifecycleAuditInitializationFilter`, `AbsoluteSessionTimeoutFilter`, `SessionLifecycleLogoutHandler`, `SessionRepositoryOidcBackChannelLogoutHandler`, `ContentNegotiatingSessionExpiredStrategy`, `SessionRevocationService`, `LocalAuthorityRefreshFilter`, `AuditingInvalidSessionStrategy`, `SecurityAuditEventLogger.onSessionFixationProtection()`; **Test code:** `SessionLifecycleAuditLoggerTest`, `SessionRevocationServiceTest.expiresAndAuditsEverySessionForTheUsername()`, `LocalAuthorityRefreshFilterTest.logsAPrivilegeChangeOnceAndSavesTheRefreshedAuthenticationToTheSession()`, `InvalidSessionIntegrationTest.idleExpiredSessionIsLoggedWhenDetectedOnItsNextUse()`, `InvalidSessionIntegrationTest.unknownSessionRequestedByAnApiIsLoggedAndReceivesUnauthorizedProblemDetail()`, `AuditingInvalidSessionStrategyTest`; **Decision:** [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md), [ADR 0009](../../../adr/0009-session-audit-initialization-checked-every-request.md), [ADR 0017](../../../adr/0017-invalid-session-and-privilege-change-logging.md). |
| **Simultaneous Session Logons**<br>Decide whether to allow concurrent sessions per user, and let users see and end their active sessions. | Partial | **Concurrent sessions:** one concurrent session is permitted per user. A later successful login marks the existing session expired; its next request invalidates it. Browser navigation redirects to `/login?session-expired`; API requests receive a generic 401 Problem Details response. `SpringSessionBackedSessionRegistry` finds sessions in JDBC across instances. The expiry strategy is overridden because Spring Security's default `ConcurrentSessionFilter` response writes a plain-text expiry message without setting a status, leaving HTTP 200, which is unsuitable for browser navigation and API clients.<br><br>**User visibility:** with one session per user, the session a user can see and end is the one they are using, through `/logout`. Users are not alerted to a concurrent logon and have no account-activity history.<br><br>**Administrators:** `DELETE /admin/users/{id}/sessions` ends one user's sessions and `DELETE /admin/users/sessions` ends every user's but the caller's (see [ASVS V7.4.5](asvs.md)).<br><br>**Application code:** `WebSecurityAutoConfiguration.securityFilterChainCustomizer()` (`maximumSessions(1)`, `maxSessionsPreventsLogin(false)`), `WebSecurityAutoConfiguration.sessionRegistry()`, `ContentNegotiatingSessionExpiredStrategy`; **Test code:** `WebSecurityConfigurationSessionManagementIntegrationTest.expiredSessionRequestedByAnApiReceivesUnauthorizedProblemDetail()`, `WebSecurityConfigurationSessionManagementIntegrationTest.expiredSessionRequestedByABrowserRedirectsToLogin()`; **Decision:** [ADR 0006](../../../adr/0006-jdbc-backed-server-side-sessions.md). |
<!-- /ocsv:generated -->

### Session Management WAF Protections

<!-- ocsv:generated source="cheatsheets/Session_Management_Cheat_Sheet.md" source-ref="7deb20b" code-ref="dcf2e27" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Session Management WAF Protections**<br>Use a web application firewall to enforce session protections when the application code itself cannot be changed. | Deployment responsibility | A web application firewall is infrastructure the application cannot configure from within; deploying one, and tuning session-related rules (ID-format anomalies, cookie tampering), is an edge/deployment decision. |
<!-- /ocsv:generated -->

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
   Administration changes already require a login no older than 15 minutes
   ([ADR 0023](../../../adr/0023-recent-login-for-administration-changes.md)).
4. Database encryption, backups, retention, and access monitoring for session
   tables and their serialized attributes.
5. Whether `Clear-Site-Data` is appropriate after testing the deployed HTTPS
   and OIDC flows, and that the application is served at the root context path,
   which the `__Host-id` cookie requires.
6. Whether suspicious IP/user-agent changes, invalid-session attempts, and
   session-ID guessing are monitored, including trusted data sources, thresholds,
   alert routing, and privacy/retention controls. Do not use IP or user-agent
   changes as automatic invalidation criteria without a product decision.

Related documentation: [Authentication](authentication.md),
[HTTP security headers](headers.md), [Logging](../06-logging-and-monitoring/README.md), and
[ADR 0006](../../../adr/0006-jdbc-backed-server-side-sessions.md).
