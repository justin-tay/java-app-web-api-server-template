# ADR 0024: Passkey login bound to the local user

## Status

Accepted

## Context

Sign-in goes through Keycloak, which is managed outside the application, so
the application cannot enable Keycloak's own passkey support. Some deployments
also need a direct sign-in for the application that does not depend on a
redirect to the provider.

Authorisation is already local (ADR 0005 and ADR 0015): Keycloak supplies only
the `preferred_username`, and roles come from the local user, group, and role
model. A passkey therefore needs to prove who the user is, not what they may do.

## Decision

The application is the WebAuthn relying party, using Spring Security's
`webauthn()` support and its JDBC credential storage. Passkeys are opt-in
through a property and off by default; when off, no WebAuthn beans, endpoints,
or tables are active.

A passkey is another way to authenticate as the same `AppUser`, not a separate
identity. `app_user.id`, already a random, immutable UUID, is the WebAuthn user
handle, as Keycloak uses the user's internal identifier. Usernames cannot be
renamed, so the `name` in Spring's `user_entities` table never has to be
synchronised. Deleting a user deletes their passkeys.

A passkey login produces a principal with the same local `ROLE_` authorities as
an OIDC login, resolved through `LocalAuthorityLookup`, with no call to
Keycloak. `LocalAuthorityRefreshFilter` covers passkey sessions too, so a
disabled or deleted user is deauthenticated on the next request. Keycloak
back-channel logout cannot end a passkey session, so passkey sessions have
their own lifetime.

A user registers a passkey from a session that authenticated recently, using
the `auth_time` check of ADR 0023. A passkey session may add a further passkey
only if it, too, is recent: the time of a passkey login is recorded in the
session, and the same check then also admits a recent passkey login for
administration changes. Credentials are discoverable, user verification is
required, attestation is `none`, and a user may hold at most 10.

The relying party ID and allowed origins are properties with no production
default; startup fails if they are missing while passkeys are enabled. Which
authenticators are allowed is left to the adopter.

Registration, removal, login success, login failure, and a signature counter
that goes backwards are logged with the existing session lifecycle field
vocabulary, with an authentication method value that tells a passkey login from
an OIDC one.

## Consequences

Users keep one identity and one set of roles whichever way they sign in, and
revoking a user in the local model revokes both.

The relying party ID is part of every passkey. Changing it later invalidates all
registered passkeys, so an adopter must choose it once.

A passkey session has no Keycloak session behind it, so an identity provider
side sign-out or account disablement is not seen until the local user is
disabled. Adopters who need the provider to be authoritative should leave
passkeys off.

Resetting a user's passkeys means deleting their credentials; the user handle
is the primary key and is not rotated.
