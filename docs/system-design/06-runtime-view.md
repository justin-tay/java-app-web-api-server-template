# 6. Runtime View

## Request handling shape

Every request passes through, in order: Tomcat, Spring Security's
`HttpFirewall` (rejecting malformed requests before they reach any filter),
the authentication/session/authorization filter chain, then Spring MVC
controllers in `api`. Errors at any stage are normalized to RFC 9457 Problem
Details rather than leaking stack traces or framework-specific error pages;
see [Error responses](08-crosscutting-concepts/security/error-responses.md)
for the full mapping and [ADR 0013](../adr/0013-rfc-9457-problem-details.md)
for why.

## OIDC authorization-code flow

The application is an OIDC relying party for Keycloak; see
[Authentication](08-crosscutting-concepts/security/authentication.md) for
the client configuration this sequence depends on.

```mermaid
sequenceDiagram
    autonumber

    participant User
    participant Browser
    participant Application Server
    participant Keycloak

    User->>Browser: Navigate to user information page
    Browser->>Application Server: Request /login-user
    Application Server->>Application Server: Request is unauthenticated
    Application Server-->>Browser: Redirect to OIDC authorization start
    Browser->>Application Server: Request /oauth2/authorization/keycloak
    Application Server-->>Browser: Redirect with authorization request
    Browser->>Keycloak: Authorization request
    Keycloak-->>Browser: Present login page
    User->>Browser: Enter credentials
    Browser->>Keycloak: Submit credentials
    Keycloak-->>Browser: Redirect with authorization code
    Browser->>Application Server: Request /login/oauth2/code/keycloak
    Application Server->>Application Server: Create client assertion
    Application Server->>Keycloak: Token request with private_key_jwt
    Keycloak->>Application Server: Read public client JWKS
    Application Server-->>Keycloak: Public signing/encryption keys
    Keycloak-->>Application Server: Return access, refresh, and ID tokens
    Application Server-->>Browser: Redirect to /login-user
    Browser->>Application Server: Request /login-user
    Application Server-->>Browser: Authenticated response
```
