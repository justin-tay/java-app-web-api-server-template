# ADR 0006: TLS and OAuth client key management

**Status:** Accepted

## Decision

Production traffic uses TLS 1.2 or TLS 1.3 with the configured strong cipher
suites. The OAuth2 client authenticates to Keycloak with `private_key_jwt`;
Keycloak obtains the corresponding public key from the application's public
JWKS endpoint.

## Context

TLS protects traffic in transit. Asymmetric client authentication avoids a
long-lived client secret shared between the application and identity provider.
Private key material is supplied through protected deployment configuration, not
embedded in source or exposed through the JWKS endpoint.

## Consequences

Local and test profiles may disable TLS only for local HTTP tooling. Production
deployments must provide certificate, trust, and private-key material securely,
and rotate OAuth client signing keys in coordination with Keycloak. The local
JWKS fixture is for development and tests only. See
[`docs/security-authentication.md`](../security-authentication.md) for
operational details.
