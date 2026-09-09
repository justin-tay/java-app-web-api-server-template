# ADR 0004: Keycloak authentication and local authorisation

**Status:** Accepted

## Decision

Keycloak is the OpenID Connect identity provider and credential authority. The
application authenticates browser users through the authorization-code flow,
then determines access from its own local user, group, and role model.

Local usernames correspond to Keycloak `preferred_username` values. Keycloak
realm and client roles are not used as application authorities, and the
application neither stores passwords nor manages Keycloak credentials.

## Context

Separating authentication from application authorization lets the application
manage its own access model without duplicating identity credentials or coupling
its permissions to Keycloak role administration. A valid Keycloak login alone
does not grant application access: the matching local user must exist and be
enabled.

## Consequences

Keycloak usernames must be unique and immutable. Renaming one requires a
coordinated migration. Authorization changes are made through the application's
local model, while identity lifecycle and credentials remain Keycloak concerns.
Implementation detail is maintained in
[`docs/security-authentication.md`](../security-authentication.md) and
[`docs/security-authorization.md`](../security-authorization.md).
