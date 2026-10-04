# Authentication

The authentication posture of the application is recorded here against the
[OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html),
alongside the template's OpenID Connect (OIDC) relying-party configuration:
JWKS key handling, token validation, and logout. Credential collection,
password policy, storage, brute-force protection, and multi-factor
authentication are Keycloak's responsibility, not this application's; the
production identity provider must also be reviewed against the cheat sheet.

## Authentication model

The application is an OIDC relying party for Keycloak. Browser requests are
authenticated with the authorization-code flow (documented as a request
sequence in [Runtime View](../../06-runtime-view.md#scenario-oidc-login));
unauthenticated requests are redirected to the configured provider. All
application routes require authentication except the public `/oauth2/jwks`
endpoint (restricted to anonymous callers) and the `/app/health` health check
with its `/app/health/liveness` and `/app/health/readiness` groups (permitted to
all callers), plus the login, OAuth2 redirect, and back-channel
logout endpoints that Spring Security's `oauth2Login()` and `oidcLogout()`
handle before authorization applies.

Each Keycloak client configured for the application uses `private_key_jwt`,
publishes the application's public keys through `/oauth2/jwks`, and is
configured for back-channel logout.

## Passkeys

Passkeys are an optional second way to authenticate as the same local user, for
a deployment that needs a direct sign-in that does not redirect to Keycloak
([ADR 0024](../../../adr/0024-passkey-login-bound-to-local-user.md)). Keycloak
stays the way a user first signs in and registers a passkey, and the fallback
when a passkey is lost. The application, not Keycloak, is the WebAuthn relying
party, because the identity provider is managed elsewhere.

They are off by default. Enabling them needs the relying party ID and the exact
origins that may use a passkey, and startup fails without them:

```yaml
commons:
  security:
    passkeys:
      enabled: true
      relying-party:
        id: app.example.com
        name: Example application
      allowed-origins:
      - https://app.example.com
```

The relying party ID is part of every passkey, so changing it later invalidates
all of them; choose it once. The remaining properties have defaults:
`max-per-user` (10), `registration-max-age` (15 minutes), and
`session-absolute-timeout` (8 hours).

| Request | Purpose |
| --- | --- |
| `POST /webauthn/authenticate/options`, `POST /login/webauthn` | Log in. Public. |
| `POST /webauthn/register/options`, `POST /webauthn/register` | Register a passkey. |
| `DELETE /webauthn/register/{id}` | Remove one of the caller's own passkeys. |
| `GET /account/passkeys`, `PATCH /account/passkeys/{id}` | List and rename the caller's passkeys. |
| `GET /admin/users/{id}/passkeys`, `DELETE /admin/users/{id}/passkeys/{credentialId}` | List and revoke a user's passkeys (`USER_MANAGE`). |

Every state-changing request carries a CSRF token like any other.

* **One identity.** A passkey does not create a separate user. The user's
  `app_user.id`, a random UUID that never changes, is the WebAuthn user handle,
  as Keycloak uses the user's internal ID. Usernames cannot be renamed, so the
  username stored beside the handle never has to be synchronised. Deleting a
  user deletes their passkeys.
* **Roles are local.** After a passkey login the session carries the same local
  `ROLE_` authorities an OIDC login would, from the user, group, and role model.
  `LocalAuthorityRefreshFilter` reloads them on every request for a passkey
  session too, so disabling or deleting the user ends it on the next request,
  and no call to Keycloak is made. The passkey proves who the user is; it
  carries no roles of its own.
* **Policy.** Credentials must be discoverable, user verification is required,
  and attestation is `none`, so the authenticator's make and model is not asked
  for. Restricting which authenticators are allowed is left to the adopter.
* **Registration needs a recent login.** Registering a passkey needs an OIDC
  login whose `auth_time`, or a passkey login, no older than
  `registration-max-age`; otherwise the request is answered with the
  `reauthentication-required` problem of the administration API
  ([ADR 0023](../../../adr/0023-recent-login-for-administration-changes.md)), so
  a taken-over session cannot enrol an attacker's passkey. The same recentness
  check now admits a fresh passkey login for administration changes.
* **Reauthenticating.** The `reauthentication-required` problem carries
  `max_age` (seconds), `method` (`oidc` or `passkey`, from the session's
  principal), and for OpenID Connect a `reauthentication_uri`
  (`/oauth2/authorization/{registrationId}?max_age=0`) naming the provider the
  session logged in with, so it stays correct with several providers. A
  passkey session gets no URI; the client runs the WebAuthn ceremony. The
  method is a hint: the server accepts any login that is recent enough, as a
  user could log out and sign in another way. This is a recency check, not a
  step-up; requiring `acr` or `amr` is the adopter's decision. The window runs
  from login, not from the last request. A client keeps the request it could
  not make and, after reauthenticating, replays it only if `/login-user`
  returns the same `id`.
* **Cloned authenticators.** A login whose signature counter did not increase
  is refused and logged as `passkey_signature_counter_regression`. A counter
  that is zero both times is allowed, because synced passkeys report zero.
* **Sessions.** A passkey login gets a new session ID and the concurrent-session
  limit of an OIDC login, so a user has one session across both methods. There
  is no Keycloak session behind it, so back-channel logout cannot end it; it has
  its own [absolute timeout](sessions.md#current-configuration), and a Keycloak
  account disabled at the provider is not seen until the local user is disabled.
  A deployment that needs the provider to stay authoritative should leave
  passkeys off.

## Authentication pathways

OIDC login through Keycloak is always available. Passkey login is a second pathway that exists only when passkeys are enabled. Both end in the same local user, and the same controls apply to both once a session exists.

| | OIDC login through Keycloak | Passkey login (optional) |
| --- | --- | --- |
| Entry | Redirect to Keycloak, then the OAuth2 redirect endpoint | `POST /webauthn/authenticate/options`, then `POST /login/webauthn` |
| Authentication strength | Set by the Keycloak realm (password, and any multi-factor authentication the realm requires); the application checks no `acr` or `amr` | Possession of a discoverable passkey and user verification on the authenticator; attestation is `none` |
| Local user | Must exist and be enabled | Must exist and be enabled |
| Authorities | Local `ROLE_` authorities from the user, group, and role model | The same |
| Session | New session ID, concurrent-session limit, 15-minute idle timeout, 12-hour absolute timeout | New session ID, the same concurrent-session limit and idle timeout, absolute timeout 8 hours from the login |
| Ended by | Logout, back-channel logout, the user being disabled or deleted locally | Logout, the user being disabled or deleted locally, its own absolute timeout |
| Recentness for administration changes | `auth_time` no older than 15 minutes | Time of the passkey login no older than 15 minutes |

## Client authentication

Keycloak uses the `private_key_jwt` client-authentication method. In the
Keycloak administration console this is configured by selecting **Signed JWT**
as the client authenticator.

The client registration declares the same method:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: java-app-web-api-server
            client-authentication-method: private_key_jwt
            authorization-grant-type: authorization_code
            scope:
            - openid
```

During the authorization-code token exchange,
`RestClientAuthorizationCodeTokenResponseClient` uses
`NimbusJwtClientAuthenticationParametersConverter` with the application's
private signing key to create the client assertion. The signing key is resolved
for every token request, so a rotated key is used as soon as the application
has read it. The corresponding public signing key is available to Keycloak at
`/oauth2/jwks`.

## Application JWKS and key handling

The development JWKS at `app-web-api-server/src/test/resources/jwks.json` contains private key
material: an ES512 `sig` key and an `ECDH-ES+A128KW` `enc` key. It is a fixture
for local development and tests only, and the build does not package it: the
`test` profile loads it from the test classpath, and the `local` profile and
`bin/start-api-server-tls.sh` load it from that path on disk.

`commons.security.oauth2.jwks` is a list of resource locations and has no default. When it is
unset, `JwksProperties` validation fails startup with a message naming the
property; when a location is set but unreadable or not a valid JWKS, the
`jwks` bean fails startup naming the location, without quoting its content. Do
not place private JWKs in source control, container images, or a public JWKS
endpoint ([ADR 0018](../../../adr/0018-development-fixtures-kept-out-of-production.md)).

With more than one location, each holds the keys of one use (`sig` or `enc`)
and each use comes from one location; a single location may hold both, as the
development JWKS does. Every key needs a unique `kid` and a `use`. On AWS, the
keys are kept in two Secrets Manager secrets created by
[`cdk-jwks-secret`](https://github.com/justin-tay/cdk-jwks-secret), one with
`use: 'sig'` and one with `use: 'enc'`, and named with the `commons-aws`
module's `aws-secretsmanager:` prefix, which reads the raw `AWSCURRENT` value
of a secret by its name or ARN:

```yaml
commons:
  security:
    oauth2:
      jwks:
      - aws-secretsmanager:<sig secret name or ARN>
      - aws-secretsmanager:<enc secret name or ARN>
```

As environment variables these are `COMMONS_SECURITY_OAUTH2_JWKS_0` and
`COMMONS_SECURITY_OAUTH2_JWKS_1`. The prefix is unrelated to Spring Cloud AWS's
`spring.config.import=aws-secretsmanager:`, which would flatten the secret into
configuration properties; do not import the JWKS secrets that way. The
`SecretsManagerClient` takes its region and credentials from the AWS SDK default
chain, such as an ECS task role, and needs only `secretsmanager:GetSecretValue`
on the two secrets (and `kms:Decrypt` on a customer-managed key), which
`jwksSecret.grantRead(role)` grants
([ADR 0020](../../../adr/0020-jwks-rotation-from-aws-secrets-manager.md)).

`RefreshingJwks` reads every location again each
`commons.security.oauth2.jwks-refresh-interval` (default `1h`, from `1m` to
`1d`), so keys rotated at the source, every 28 days by default for
`cdk-jwks-secret`, are picked up without a restart. Keep the interval a small
fraction of the rotation interval. A read that fails logs a WARN and keeps the
last good keys. When there is no signing key, or an ID token names an `enc` key
the application has not read yet, it reads the locations again at once, at
most once every 30 seconds. It follows the rotation rules of `cdk-jwks-secret`:

* **Signing:** the first `sig` key that has its private part signs; a retired
  key keeps only its public part, so it is never chosen.
* **Publishing:** `JwksController` publishes the public components of every
  `sig` key, and of every `enc` key except the first when there are three,
  which is the key the next rotation deletes. Private key material is never
  published.
* **Decrypting:** every `enc` key keeps its private part and decrypts an ID
  token whose `kid` names it.

A newly deployed secret is empty until its first rotation, shortly after the
stack is deployed. The application still starts, but the `jwks` health
contributor, part of the readiness group at `/app/health/readiness` on the
management port, is DOWN until there is a signing key and while any location
has no keys, so a load balancer that checks readiness sends it no traffic.
Liveness at `/app/health/liveness` stays UP, so the orchestrator does not
restart it.

During a token exchange Keycloak uses `/oauth2/jwks` to obtain:

* The public signing key for verifying the `private_key_jwt` client assertion.
* The public encryption key, when ID-token encryption is enabled for the
  Keycloak client.

## ID-token and access-token validation

The custom `JwtDecoderFactory<ClientRegistration>` is used because the default
`OidcIdTokenDecoderFactory` is not sufficiently customizable for encrypted
ID-token support. The decoder:

* accepts signed RS256 ID tokens;
* selects only keys marked for signature use from the provider JWKS, preventing
  RSA encryption keys and unrelated EC signature keys from being selected;
* when the application's JWKS has `enc` keys, decrypts an ID token encrypted to
  one of them (a JWE nesting the signed ID token) with the key its `kid` names,
  accepting only that key's own `alg` and any RFC 7518 content encryption
  (`A128CBC-HS256`, `A192CBC-HS384`, `A256CBC-HS512`, `A128GCM`, `A192GCM`,
  `A256GCM`), and rejects an ID token that is not encrypted;
* applies `OidcIdTokenValidator`, which validates the OIDC ID-token claims for
  the client registration.

Because the application rejects an unencrypted ID token while it has `enc`
keys, the Keycloak client must have ID-token encryption turned on whenever an
`enc` key is configured, and off (with no `enc` location) otherwise.
`bin/configure-keycloak.js` sets the client's ID Token Encryption Key
Management Algorithm to `ECDH-ES+A128KW` and its Content Encryption Algorithm
to `A128CBC-HS256`, Keycloak's default. `ECDH-ES` key management needs
Keycloak 26.0 or later.

The application does not read Keycloak realm or client roles (such as the
`realm_access.roles` claim) from the access token or ID token.
`LocalAuthoritiesOidcUserService` resolves the OIDC user's username claim (the
registration's `user-name-attribute`, see [Authorization](authorization.md#identity-resolution))
to an enabled local user and derives authorities from the roles of that
user's local groups, exposing each as a Spring authority with the `ROLE_`
prefix; see [Authorization](authorization.md).

## Logout

### Relying-party initiated logout

`JsonAwareOidcLogoutSuccessHandler`, a subclass of Spring Security's
`OidcClientInitiatedLogoutSuccessHandler`, uses Keycloak's
`end_session_endpoint` when the application logs a user out. It supplies an
`id_token_hint`; after logout, Keycloak redirects the browser to
`commons.security.logout.post-logout-redirect-uri`, by default
`{baseUrl}/login?logout`. The redirect URI must also be a valid post logout
redirect URI of the Keycloak client.

When ID tokens are encrypted ([ADR 0020](../../../adr/0020-jwks-rotation-from-aws-secrets-manager.md)),
the `id_token_hint` is the signed JWT nested in the JWE, not the JWE, which is
encrypted to the application's key and which Keycloak could not read. The ID
token decoder gives the decoded token that value, as OpenID Connect Core
(section 3.1.2.1) has the client do before using an ID token as a hint.

A browser navigation is redirected to Keycloak. A single-page application calls
logout with `fetch`, which cannot follow that redirect, so a request that
accepts `application/json` and not `text/html` is answered with `200` and
`{"logoutUrl": "..."}` instead, and the application must then set
`window.location` to that URL, or Keycloak's session stays alive
([ADR 0025](../../../adr/0025-json-logout-url-for-single-page-applications.md)):

```js
const response = await fetch('/logout', {
  method: 'POST',
  headers: { Accept: 'application/json', 'X-XSRF-TOKEN': xsrfToken },
});
const { logoutUrl } = await response.json();
window.location.assign(logoutUrl);
```

A user who logged in with a passkey has no Keycloak session to end, so the URL
is the application's own.

### Back-channel logout

The application accepts Keycloak back-channel logout notifications using:

```java
http.oidcLogout(oidcLogout -> oidcLogout.backChannel(backChannel -> backChannel
    .logoutHandler(new SessionRepositoryOidcBackChannelLogoutHandler(oidcSessionRegistry,
        sessionRepository, sessionLifecycleAuditLogger))));
```

Spring Security's `OidcBackChannelLogoutFilter` validates the logout token
(signature against the provider JWKS, issuer, audience, `iat`, `jti`, the
back-channel logout `events` member, `sub` or `sid`, and no `nonce`). At
login, Spring Security links the Keycloak session (`sid` and `sub`) to the
local session ID in the `OidcSessionRegistry`.
`SessionRepositoryOidcBackChannelLogoutHandler` removes the entries the token
names, logs each linked session as `destroy_session` with
`session.termination_reason` `back_channel_logout`, and deletes it from the
JDBC session repository, so its `SPRING_SESSION` row is gone and the browser's
next request is unauthenticated.

Spring Security's default `OidcBackChannelLogoutHandler` is not used. It ends
each linked session by posting back to the application with the session ID in
a `JSESSIONID` cookie; setting its cookie name to `id` is not enough, because
it sends the raw session ID while Spring Session's session cookie carries the ID
Base64-encoded, so that internal request resumes no session and the session
survives. Deleting through the repository also avoids the application having
to call itself at the URL Keycloak used.

**Any instance.** The `OidcSessionRegistry` is `JdbcOidcSessionRegistry`
(`OidcLoginSecurityAutoConfiguration.oidcSessionRegistry()`), which keeps each
link in the `OIDC_SESSION` table (`com/example/commons/session/oidc/jdbc/schema.yaml`) next to the
session tables. Keycloak sends the notification server-to-server with no
session cookie, so a load balancer cannot route it to the instance that handled
the login; because the link is in the database, whichever instance receives it
finds the session. A link is claimed by deleting its row, so when the
notification reaches two instances at once, only one of them ends the session.

The link is written at login, before Spring Session saves the session row, so
the table has no foreign key to `SPRING_SESSION`. A link whose session has
ended another way (local logout, timeout) is harmless, because a back-channel
logout that finds it finds no session to end, and saving a new link deletes
the links of sessions that no longer exist once they are older than ten
minutes. Replace the registry by defining an `OidcSessionRegistry` bean.

`OidcBackChannelLogoutIntegrationTest` logs in through a stub OpenID Provider
and asserts that a back-channel logout removes the session's `SPRING_SESSION`
row.

For each Keycloak client:

* Set **Front channel logout** to **Off**.
* Set the Backchannel logout URL to
  `{application base URL}/logout/connect/back-channel/{registration-id}`,
  where `{registration-id}` is the Spring client-registration ID (`keycloak`
  in this template).

## OWASP control implementation

### Status meanings

| Status | Meaning |
| --- | --- |
| Implemented | The application's own code or configuration fulfills the recommendation, or it is satisfied by verified framework/dependency behavior; how is explained in the same row. |
| Partial | The recommendation is only partly satisfied; the remainder is explained in the same row. |
| Not implemented | No application or identity-provider mechanism currently satisfies the recommendation. |
| Not applicable | The recommendation does not apply to this template at all; no mechanism, internal or external, is expected to address it. |
| Delegated to identity provider | Keycloak, not the application, owns this behavior, whether because the recommendation is satisfied outside the application or because there is simply nothing for the application itself to implement; the production identity provider must be reviewed against the cheat sheet separately. |
| Delegated to identity provider, not enabled by default | Keycloak can provide this capability, but the supplied realm configuration does not enable it. |
| Deployment decision required | The application cannot safely choose the value; it depends on infrastructure, identity-provider topology, or an operational decision the deployer must make. |

This section follows the [Authentication Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)'s
own subsection headings, in its own order, "Introduction" excepted since it
is motivational rather than actionable. Illustrative sub-examples nested
under a recommendation (such as the sample login/recovery/account-creation
error text under "Authentication and Error Messages", or the "References"
lists) are folded into the parent row rather than given their own row,
since they carry no separate testable claim.

### Authentication General Guidelines

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **User IDs**<br>Generate user IDs randomly so they are neither predictable nor sequential. | Partial | The local `AppUser` record (the application's own authorization identity) has a randomly generated UUID primary key that never changes and is never reused, per `AbstractAuditableEntity`. The upstream Keycloak identity's own internal ID is entirely Keycloak's responsibility.<br><br>**Application code:** `AbstractAuditableEntity`, `AppUser`. |
| **Usernames**<br>Let users log in with a verified email address or a username of their own choosing. | Delegated to identity provider | The application resolves the OIDC `preferred_username` claim to the immutable local `username` field (validated by the `@Username` constraint on `AppUser`/`UserCreateRequest`) as a lookup key; it does not accept, register, or validate a username as a login credential itself, so the cheat sheet's case-sensitivity/format guidance for a login form does not apply. See [Authorization](authorization.md). |
| **Authentication Solution and Sensitive Accounts**<br>Keep sensitive internal accounts off front-end logins, and do not reuse the internal authentication solution for public access. | Deployment decision required | The application does not distinguish an internal/service-account population from browser users. Ensure the Keycloak realm and client used by end users are not also used to authenticate backend, database, or administrative accounts. |
| **Implement Proper Password Strength Controls**<br>Enforce sensible minimum and maximum password lengths, allow all characters, and block known-breached passwords. | Delegated to identity provider | The application never receives, renders, or validates a password field; Keycloak's realm password policy owns strength rules. Neither `WebSecurityAutoConfiguration` nor `WebSecurityConfiguration` defines a credential input of its own. |
| **Implement Secure Password Recovery Mechanism**<br>Provide a secure password recovery flow, following the Forgot Password Cheat Sheet. | Delegated to identity provider | Password reset is a Keycloak realm flow (`bin/configure-keycloak.js` provisions no custom recovery flow); the application exposes no forgot-password endpoint. |
| **Store Passwords in a Secure Fashion**<br>Store passwords using a strong password-hashing technique, following the Password Storage Cheat Sheet. | Delegated to identity provider | The application has no password column, hash, or credential store of any kind; `AppUser` carries only `username`, `name`, and `email`. Keycloak owns hashing and storage. |
| **Compare Password Hashes Using Safe Functions**<br>Compare password hashes with a vetted, constant-time library function rather than hand-written comparison. | Delegated to identity provider | Password comparison never occurs in application code; there is no such code path to review. |
| **Change Password Feature**<br>Require an active session and the current password before allowing a password change. | Delegated to identity provider | Handled entirely by Keycloak's account console, outside this application's routes and controllers. |
| **Transmit Passwords Only Over TLS or Other Strong Transport**<br>Serve the login page and every authenticated page only over TLS. | Implemented | Credential entry occurs on Keycloak's hosted login page, outside this application. Token exchange and every authenticated application route are TLS-protected by this application's own listener. See [Hardening](hardening.md) sections 6.2-6.5. Confirm against the deployed service that TLS protects the whole authenticated session, including Keycloak's own login and consent pages.<br><br>**Application configuration:** `server.ssl`. |
| **Require Re-authentication for Sensitive Features**<br>Ask for the user's credentials again before sensitive account changes or critical transactions. | Implemented | Every change made through the administration API (users, groups, and roles) needs a login no older than 15 minutes, from the ID token's `auth_time`; otherwise it is answered with a `reauthentication-required` problem and the client logs in again with `max_age=0`, which makes Keycloak authenticate the user afresh. Ending sessions is exempt, so incident response is never delayed ([ADR 0023](../../../adr/0023-recent-login-for-administration-changes.md)).<br><br>**Application code:** `AdminReauthenticationInterceptor`, `RecentAuthentication`, `MaxAgeAuthorizationRequestResolver`; **Test code:** `AdminApiIntegrationTest.aChangeNeedsARecentLoginButAReadDoesNot()`. |
| **Re-authentication After Risk Events**<br>Require re-authentication after risk events such as account recovery, password resets, or suspicious activity. | Not implemented | No device, IP, or behavioral risk signal triggers re-authentication; see "Adaptive or Risk Based Authentication" below, which covers the same gap. |
| **Consider Strong Transaction Authentication**<br>Consider requiring a second factor before sensitive operations, following the Transaction Authorization Cheat Sheet. | Not implemented | The application authenticates its own back-channel calls to Keycloak with `private_key_jwt` (TLS client authentication is not used for this), and defines no per-transaction second factor for any administration action. See [Hardening](hardening.md) section 6.1 for the mTLS deployment decision. |
| **Authentication and Error Messages**<br>Return the same generic error message and status for every authentication failure so accounts cannot be enumerated. | Partial | A failed Keycloak login is handled entirely by Keycloak's own login page. Once Keycloak issues a successful authentication, `LocalAuthoritiesOidcUserService.unauthorized()` rejects a missing claim, an unknown local user, and a disabled local user with the same generic `local_user_not_authorized` OAuth2 error, and Spring Security's default failure handler redirects to a generic `/login?error` page. Verify the rendered error page never distinguishes these outcomes from each other or from a Keycloak-side rejection.<br><br>**Application code:** `LocalAuthoritiesOidcUserService.unauthorized()`; **Spring Security default:** OAuth2 login failure handling. |

#### Protect Against Automated Attacks

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Multi-Factor Authentication**<br>Implement MFA wherever feasible, as the strongest defense against password-based attacks. | Delegated to identity provider, not enabled by default | Keycloak can require OTP or WebAuthn per realm or per user, but the supplied local development realm does not enable it; `bin/configure-keycloak.js` provisions no realm MFA policy. Enabling MFA is a production identity-provider decision. FIDO2/WebAuthn passkeys specifically are also not configured in the supplied realm. |
| **Login Throttling**<br>Limit failed login attempts per account, for example with an account lockout policy. | Delegated to identity provider, not enabled by default | The application never sees a submitted password, so it cannot throttle or lock out credential-guessing attempts itself, and there is no application-rendered login form to throttle. Login throttling and account lockout are entirely a Keycloak realm policy, but the supplied local development realm does not enable them; `bin/configure-keycloak.js` creates the realm without brute-force detection (no `bruteForceProtected` or lockout settings). Enabling brute-force detection and choosing its threshold, observation window, and lockout duration is a production identity-provider decision. See [Hardening](hardening.md) section 5.2. |
| **CAPTCHA**<br>Treat CAPTCHA as a defense-in-depth control against automated login, ideally required only after a few failed attempts. | Delegated to identity provider | Any CAPTCHA challenge would be rendered on Keycloak's hosted login page, not by the application; the supplied realm configuration does not enable one. |
| **Security Questions and Memorable Words**<br>If security questions or memorable words are used against automated attacks, choose them carefully and do not count them as MFA. | Not applicable | The application implements no knowledge-based recovery mechanism of its own. |

### Logging and Monitoring

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Logging and Monitoring**<br>Log and monitor authentication failures, password failures, and account lockouts to detect attacks in real time. | Partial | The application logs every authentication failure it can observe: `SecurityAuditEventLogger` records a failed `login` (for example an unknown or disabled local user rejected after Keycloak authenticated them) with outcome, user name, and exception type, never credentials or tokens. It also logs successful logins, which the Cheat Sheet doesn't ask for but which help reconstruct an account's activity. Password failures and account lockouts happen inside Keycloak, since the application never sees a submitted password, so they must be logged and monitored in Keycloak's own event log; real-time monitoring of either stream is a deployment concern. Confirm in the deployed service that these events appear without credentials or tokens.<br><br>**Application code:** `SecurityAuditEventLogger`. See [Logging](../06-logging-and-monitoring/README.md). |

### Use of authentication protocols that require no password

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **OAuth 2.0 and 2.1**<br>Use OAuth only as an authorization framework for delegated API access, following the OAuth 2.0 Cheat Sheet. | Not applicable | The application uses OpenID Connect, not bare OAuth 2.0/2.1, for authentication; see OpenID Connect (OIDC) below. |
| **OpenID Connect (OIDC)**<br>Use OIDC for authentication through a well-maintained library, validating the ID token's issuer, audience, signature, and expiry. | Implemented | The application delegates authentication to Keycloak through Spring Security's OAuth2 Login/OIDC client (`spring.security.oauth2.client`, `oauth2Login()`) rather than a custom credential scheme, using well-maintained libraries (`spring-boot-starter-oauth2-client`, Nimbus JOSE+JWT) and provider discovery/JWKS rather than embedded cryptography. ID tokens are validated for issuer, audience, signature, and expiration; see [ID-token and access-token validation](#id-token-and-access-token-validation) above. Confirm a tampered, expired, or wrong-audience ID token is rejected.<br><br>**Application code:** `jwtDecoderFactory()`, `oidcIdTokenValidator()`; **Test code:** `WebSecurityAutoConfigurationTest.oidcIdTokenValidatorRejectsUnexpectedIssuerAudienceAndAuthorizedParty()`. See [ADR 0007](../../../adr/0007-tls-and-oauth-client-key-management.md). |
| **SAML**<br>Consider SAML 2.0, the XML-based federation protocol common in enterprise single sign-on, as a password-free option. | Not applicable | The template uses OIDC exclusively. |
| **FIDO**<br>Consider FIDO public-key authentication, the basis of FIDO2/WebAuthn passkeys, for passwordless or second-factor login. | Not implemented | FIDO2/WebAuthn passkeys are not configured in the supplied Keycloak realm. |

### Password Managers

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Password Managers**<br>Keep login forms password-manager friendly: standard HTML fields, long passwords, and paste and Tab navigation allowed. | Delegated to identity provider | The credential-entry form is Keycloak's hosted login page, not an application-rendered form; the application renders no form for a password manager to interact with. |

### Changing A User's Registered Email Address

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Recommended Process If the User HAS Multifactor Authentication Enabled**<br>Confirm an email change with MFA, then notify the old address and require confirmation from the new one via single-use nonces. | Not applicable | There is no self-service email-change flow to apply this to; see Notes below. |
| **Recommended Process If the User DOES NOT HAVE Multifactor Authentication Enabled**<br>Confirm an email change with the password, then require confirmation from both the old and new addresses via single-use nonces. | Not applicable | There is no self-service email-change flow to apply this to; see Notes below. |
| Notes on the Above Processes | Not applicable | The local user's `email` field is maintained only through the administration API by an authorised holder of `ROLE_USER_MANAGE` (see [Authorization](authorization.md)), not through a user-initiated self-service flow, so the cheat sheet's confirmation/nonce process does not apply.<br><br>**Application code:** `UserAdminController`, `AdministrationService`. |

### Adaptive or Risk Based Authentication

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Adaptive or Risk Based Authentication**<br>Vary the authentication required according to contextual risk signals such as device, location, IP address, time, or data sensitivity. | Not implemented | No device, geolocation, or behavioural risk signal influences the authentication or session outcome. |

## Required production decisions

Before production use, the service owner must record and implement decisions for:

1. Keycloak realm/client topology that keeps internal or service accounts out of
   the browser-facing OIDC client.
2. Whether MFA (OTP, WebAuthn) is required, and for which users or realms.
3. Keycloak brute-force detection: lockout threshold, observation window,
   lockout duration, and whether CAPTCHA is also required.
4. Whether specific risk events require re-authentication, and whether
   administrative actions require step-up MFA (`acr`) on top of the recent login
   they already need, coordinated with [Sessions](sessions.md).
5. Whether mTLS is required for machine/API clients, coordinated with
   [Hardening](hardening.md) section 6.1.
6. Password-recovery, account-lockout communication, and any self-service
   profile-change policy configured in the Keycloak realm.

Related documentation: [Authorization](authorization.md),
[Sessions](sessions.md), [HTTP security headers](headers.md),
[Logging](../06-logging-and-monitoring/README.md), [Hardening](hardening.md),
[Error responses](error-responses.md),
[ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md),
[ADR 0007](../../../adr/0007-tls-and-oauth-client-key-management.md), and
[ADR 0020](../../../adr/0020-jwks-rotation-from-aws-secrets-manager.md).

