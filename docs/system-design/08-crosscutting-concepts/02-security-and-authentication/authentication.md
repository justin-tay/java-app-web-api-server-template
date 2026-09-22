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
sequence in [Runtime View](../../06-runtime-view.md#oidc-authorization-code-flow));
unauthenticated requests are redirected to the configured provider. All
application routes require authentication except the public `/oauth2/jwks`
endpoint.

Each Keycloak client configured for the application uses `private_key_jwt`,
publishes the application's public keys through `/oauth2/jwks`, and is
configured for back-channel logout.

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
private signing key to create the client assertion. The corresponding public
signing key is available to Keycloak at `/oauth2/jwks`.

## Application JWKS and key handling

The development JWKS at `src/main/resources/jwks.json` contains private key
material. It is a fixture for local development and tests only; it must not be
deployed.

For a real deployment, provide an equivalent private JWKS through a protected
resource, set `app.jwks` to that resource location, and rotate signing and
encryption keys in coordination with Keycloak. Do not place private JWKs in
source control, container images, or a public JWKS endpoint.

`WebSecurityConfiguration` loads the configured JWKS into a `JWKSet`.
`JwksController` publishes only public key components at `/oauth2/jwks`;
`JWKSet.toString()` does not include private key material.

During a token exchange Keycloak uses that endpoint to obtain:

* The public signing key for verifying the `private_key_jwt` client assertion.
* The public encryption key if ID-token encryption is enabled for the Keycloak
  client.

## ID-token and access-token validation

The custom `JwtDecoderFactory<ClientRegistration>` is used because the default
`OidcIdTokenDecoderFactory` is not sufficiently customizable for encrypted
ID-token support. The current decoder:

* accepts signed RS256 ID tokens;
* selects only keys marked for signature use from the provider JWKS, preventing
  RSA encryption keys and unrelated EC signature keys from being selected;
* applies `OidcIdTokenValidator`, which validates the OIDC ID-token claims for
  the client registration.

The supplied Keycloak configuration does **not** enable ID-token encryption.
The current decoder has no JWE decryption-key selector, so enabling encryption
in Keycloak without a matching decoder configuration will fail authentication.
If encryption is required, configure the selected JWE algorithm and a private
decryption key in the application before enabling it for the Keycloak client.

For role extraction, the application decodes the access token with an
issuer-validating decoder and reads Keycloak's `realm_access.roles` claim from
both the access token and ID token. Roles are exposed as Spring authorities
with the `ROLE_` prefix.

## Logout

### Relying-party initiated logout

`OidcClientInitiatedLogoutSuccessHandler` uses Keycloak's
`end_session_endpoint` when the application logs a user out. It supplies an
`id_token_hint`; after logout, Keycloak redirects the browser to
`{baseUrl}/login?logout`.

### Back-channel logout

The application accepts Keycloak back-channel logout notifications using:

```java
http.oidcLogout(oidcLogout -> oidcLogout.backChannel(withDefaults()));
```

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

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| User IDs | Partial | The local `AppUser` record (the application's own authorization identity) has a randomly generated UUID primary key that never changes and is never reused, per `AbstractAuditableEntity`. The upstream Keycloak identity's own internal ID is entirely Keycloak's responsibility.<br><br>**Application code:** `AbstractAuditableEntity`, `AppUser`. |
| Usernames | Delegated to identity provider | The application resolves the OIDC `preferred_username` claim to the immutable local `username` field (validated by the `@Username` constraint on `AppUser`/`UserCreateRequest`) as a lookup key; it does not accept, register, or validate a username as a login credential itself, so the cheat sheet's case-sensitivity/format guidance for a login form does not apply. See [Authorization](authorization.md). |
| Authentication Solution and Sensitive Accounts | Deployment decision required | The application does not distinguish an internal/service-account population from browser users. Ensure the Keycloak realm and client used by end users are not also used to authenticate backend, database, or administrative accounts. |
| Implement Proper Password Strength Controls | Delegated to identity provider | The application never receives, renders, or validates a password field; Keycloak's realm password policy owns strength rules. `WebSecurityConfiguration` defines no credential input of its own. |
| Implement Secure Password Recovery Mechanism | Delegated to identity provider | Password reset is a Keycloak realm flow (`bin/configure-keycloak.js` provisions no custom recovery flow); the application exposes no forgot-password endpoint. |
| Store Passwords in a Secure Fashion | Delegated to identity provider | The application has no password column, hash, or credential store of any kind; `AppUser` carries only `username`, `displayName`, and `email`. Keycloak owns hashing and storage. |
| Compare Password Hashes Using Safe Functions | Delegated to identity provider | Password comparison never occurs in application code; there is no such code path to review. |
| Change Password Feature | Delegated to identity provider | Handled entirely by Keycloak's account console, outside this application's routes and controllers. |
| Transmit Passwords Only Over TLS or Other Strong Transport | Implemented | Credential entry occurs on Keycloak's hosted login page, outside this application. Token exchange and every authenticated application route are TLS-protected by this application's own listener. See [Hardening](hardening.md) sections 6.2-6.5. Confirm against the deployed service that TLS protects the whole authenticated session, including Keycloak's own login and consent pages.<br><br>**Application configuration:** `server.ssl`. |
| Require Re-authentication for Sensitive Features | Not implemented | No step-up or re-authentication requirement is defined for sensitive administration actions (user, group, or role management). This mirrors the equivalent open item in [Sessions](sessions.md). |
| Re-authentication After Risk Events | Not implemented | No device, IP, or behavioral risk signal triggers re-authentication; see "Adaptive or Risk Based Authentication" below, which covers the same gap. |
| Consider Strong Transaction Authentication | Not implemented | The application authenticates its own back-channel calls to Keycloak with `private_key_jwt` (TLS client authentication is not used for this), and defines no per-transaction second factor for any administration action. See [Hardening](hardening.md) section 6.1 for the mTLS deployment decision. |
| Authentication and Error Messages | Partial | A failed Keycloak login is handled entirely by Keycloak's own login page. Once Keycloak issues a successful authentication, `LocalAuthoritiesOidcUserService.unauthorized()` rejects a missing claim, an unknown local user, and a disabled local user with the same generic `local_user_not_authorized` OAuth2 error, and Spring Security's default failure handler redirects to a generic `/login?error` page. Verify the rendered error page never distinguishes these outcomes from each other or from a Keycloak-side rejection.<br><br>**Application code:** `LocalAuthoritiesOidcUserService.unauthorized()`; **Spring Security default:** OAuth2 login failure handling. |
| Multi-Factor Authentication | Delegated to identity provider, not enabled by default | Keycloak can require OTP or WebAuthn per realm or per user, but the supplied local development realm does not enable it; `bin/configure-keycloak.js` provisions no realm MFA policy. Enabling MFA is a production identity-provider decision. FIDO2/WebAuthn passkeys specifically are also not configured in the supplied realm. |
| Login Throttling | Delegated to identity provider | The application never sees a submitted password, so it cannot throttle or lock out credential-guessing attempts itself, and there is no application-rendered login form to throttle. Login throttling and account lockout are entirely a Keycloak realm policy. See [Hardening](hardening.md) section 5.2. Confirm the provisioned realm's brute-force/lockout threshold, observation window, and lockout duration actually take effect. |
| CAPTCHA | Delegated to identity provider | Any CAPTCHA challenge would be rendered on Keycloak's hosted login page, not by the application; the supplied realm configuration does not enable one. |
| Security Questions and Memorable Words | Not applicable | The application implements no knowledge-based recovery mechanism of its own. |

### Logging and Monitoring

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Log authentication successes and failures | Implemented | `SecurityAuditEventLogger` records `login` with outcome, user name, and (on failure) exception type, never credentials or tokens. See [Logging](../06-logging-and-monitoring/README.md). Confirm in the deployed service that these events appear without credentials or tokens. |

### Use of authentication protocols that require no password

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| OAuth 2.0 and 2.1 | Not applicable | The application uses OpenID Connect, not bare OAuth 2.0/2.1, for authentication; see OpenID Connect (OIDC) below. |
| OpenID Connect (OIDC) | Implemented | The application delegates authentication to Keycloak through Spring Security's OAuth2 Login/OIDC client (`spring.security.oauth2.client`, `oauth2Login()`) rather than a custom credential scheme, using well-maintained libraries (`spring-boot-starter-oauth2-client`, Nimbus JOSE+JWT) and provider discovery/JWKS rather than embedded cryptography. ID tokens are validated for issuer, audience, signature, and expiration; see [ID-token and access-token validation](#id-token-and-access-token-validation) above. Confirm a tampered, expired, or wrong-audience ID token is rejected.<br><br>**Application code:** `jwtDecoderFactory()`, `oidcIdTokenValidator()`. See [ADR 0007](../../../adr/0007-tls-and-oauth-client-key-management.md). |
| SAML | Not applicable | The template uses OIDC exclusively. |
| FIDO | Not implemented | FIDO2/WebAuthn passkeys are not configured in the supplied Keycloak realm. |

### Password Managers

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Password-manager compatibility (form field types, paste, tab order) | Delegated to identity provider | The credential-entry form is Keycloak's hosted login page, not an application-rendered form; the application renders no form for a password manager to interact with. |

### Changing A User's Registered Email Address

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Recommended Process If the User HAS Multifactor Authentication Enabled | Not applicable | There is no self-service email-change flow to apply this to; see Notes below. |
| Recommended Process If the User DOES NOT HAVE Multifactor Authentication Enabled | Not applicable | There is no self-service email-change flow to apply this to; see Notes below. |
| Notes on the Above Processes | Not applicable | The local user's `email` field is maintained only through the administration API by an authorised holder of `ROLE_USER_MANAGE` (see [Authorization](authorization.md)), not through a user-initiated self-service flow, so the cheat sheet's confirmation/nonce process does not apply.<br><br>**Application code:** `UserAdminController`, `AdministrationService`. |

### Adaptive or Risk Based Authentication

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Adaptive or risk-based authentication | Not implemented | No device, geolocation, or behavioural risk signal influences the authentication or session outcome. |

## Required production decisions

Before production use, the service owner must record and implement decisions for:

1. Keycloak realm/client topology that keeps internal or service accounts out of
   the browser-facing OIDC client.
2. Whether MFA (OTP, WebAuthn) is required, and for which users or realms.
3. Keycloak brute-force detection: lockout threshold, observation window,
   lockout duration, and whether CAPTCHA is also required.
4. Whether administrative actions or specific risk events require
   re-authentication or step-up MFA, coordinated with [Sessions](sessions.md).
5. Whether mTLS is required for machine/API clients, coordinated with
   [Hardening](hardening.md) section 6.1.
6. Password-recovery, account-lockout communication, and any self-service
   profile-change policy configured in the Keycloak realm.

Related documentation: [Authorization](authorization.md),
[Sessions](sessions.md), [HTTP security headers](headers.md),
[Logging](../06-logging-and-monitoring/README.md), [Hardening](hardening.md),
[Error responses](error-responses.md),
[ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md), and
[ADR 0007](../../../adr/0007-tls-and-oauth-client-key-management.md).

