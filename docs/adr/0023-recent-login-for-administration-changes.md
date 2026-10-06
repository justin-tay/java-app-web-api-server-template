# ADR 0023: Recent login for administration changes

## Status

Accepted

## Context

A session lasts up to 12 hours, and the administration API accepted a change
from any authenticated session with the right role, however long ago its
user logged in. An unattended browser, or a session taken over at any point
in those 12 hours, could change who has access. OWASP ASVS V6.8.4 asks an
application relying on an identity provider to check the authentication
strength or recentness it needs from `acr`, `amr`, or `auth_time`, or to
document the assumption it makes instead.

Which authentication *strength* is needed depends on whether the identity
provider requires multi-factor authentication, which is an adopter decision.
Recentness does not: `auth_time` is in every ID token the provider issues.

## Decision

A state-changing request (any method but `GET`, `HEAD`, `OPTIONS`, or
`TRACE`) to `/admin/users/**`, `/admin/groups/**`, or `/admin/roles/**`
requires the user to have authenticated at the provider no longer ago than
`commons.accounts.admin.reauthentication-max-age`, 15 minutes by default,
matching the idle timeout. `AdminReauthenticationInterceptor` checks the
`auth_time` claim of the session's ID token; a login without one is treated
as too old, so the check fails closed.

A request that fails the check is answered with 401 and a Problem Details
body of type `urn:problem:reauthentication-required`, carrying the allowed
age in seconds as `max_age`, after RFC 9470's step-up challenge. It is logged
as an `authorize_access` failure with `event.reason`
`reauthentication_required`. The client sends the browser to
`/oauth2/authorization/keycloak?max_age=0`; `MaxAgeAuthorizationRequestResolver`
passes `max_age=0` on to the provider, which must then authenticate the user
again and return the new `auth_time`. Only the value `0` is passed on.

The problem also names the method the session logged in with, as `method`
(`oidc` or `passkey`), and for an OpenID Connect login a `reauthentication_uri`
that already names the same client registration and carries `max_age=0`. A
passkey login has no URI, because the client runs the WebAuthn ceremony itself.
`ReauthenticationChallenge` builds both. They are a hint to the client, not a
rule: the server accepts any login that is recent enough, since a user could
log out and sign in by another method anyway. A client keeps the request it
could not make, and after reauthenticating checks that `/login-user` returns the
same `id` before replaying it.

Ending sessions (`DELETE /admin/users/{id}/sessions` and
`DELETE /admin/users/sessions`) is exempt, so an administrator responding to
an incident is never delayed by a login.

Checking `acr` or `amr` for a stronger factor is left to the adopter, together
with the decision to require multi-factor authentication.

## Consequences

A stolen or unattended session cannot change access more than 15 minutes
after its user last authenticated. Administrators who work for longer than
that authenticate again, which is a redirect through the provider; its own
SSO session decides whether they are asked for credentials, so `max_age=0`
must be honoured by the provider for the check to mean a fresh login.

Every client of the administration API must handle the
`reauthentication-required` problem. Non-browser clients have no way to
re-authenticate and cannot make changes.

The check runs after the application's coarse permission gate, so a caller who holds no
permission of the API's domain gets 403 and is not sent to sign in again (see
[ADR 0038](0038-role-permission-model-and-account-review-classes.md) and
[Authorization](../system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md#order-of-the-checks)).

`MaxAgeAuthorizationRequestResolver` and `RecentAuthentication` live in
commons, so an application can require a recent login for its own sensitive
operations the same way.
