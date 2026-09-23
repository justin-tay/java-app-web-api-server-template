# ADR 0017: Log unrecognized session IDs and session privilege changes

## Status

Accepted

## Context

[ADR 0008](0008-session-lifecycle-audit-identifiers.md) limits session
lifecycle logging to causes the application can know accurately, and records
that JDBC expiry and arbitrary invalid-cookie attempts had no reliable,
correlated Spring hook. [ADR 0009](0009-session-audit-initialization-checked-every-request.md)
adds that each lifecycle cause should use a purpose-built Spring Security
extension point where one exists. This ADR widens that scope; ADR 0008's
audit-identifier decision is unchanged.

The OWASP Session Management Cheat Sheet asks for session timeout expiration,
invalid session activity, and privilege changes to be logged. Two of these
had a hook that was not being used:

* Spring Security's `InvalidSessionStrategy` is called when a request
  presents a session ID for which the session repository returns nothing.
  Spring Session JDBC returns nothing for a session past its idle timeout,
  so this is where idle expiry becomes visible, on the session's next use.
  The same hook fires for an ID that was ended some other way, or never
  issued; the application cannot tell these apart, because the ID is only a
  lookup key and the expired row may already be gone.
* `LocalAuthorityRefreshFilter` ([ADR 0015](0015-per-request-local-authority-refresh.md))
  already reloads a session's `ROLE_` authorities on every request, so it
  sees when they differ from the ones the session holds.

Idle expiry of a session that is never used again is still removed by JDBC
cleanup with no request to observe it.

## Decision

The application logs two more session events, stating only what it can know:

* `AuditingInvalidSessionStrategy`, configured as the security chain's
  `InvalidSessionStrategy`, logs `resume_session` with `event.outcome`
  `failure` and `event.reason` `session_not_found` when a request presents a
  session ID that is not found. The name says the session was not found, not
  that it expired, because an idle-expired, ended, and forged ID look the
  same here. The event carries no `session.id`: there is no audit identifier
  for a session the application cannot find, and neither the presented ID nor
  anything derived from it is logged. The strategy then answers exactly as
  an unauthenticated request is answered (the request is saved in the
  request cache, and the authentication entry point redirects a browser to
  login or returns a 401 Problem Details response). Spring Security also calls
  this strategy from `CsrfFilter` for a missing CSRF token; when no invalid
  session ID was presented, the strategy answers with the access-denied
  handler instead and logs nothing.
* `LocalAuthorityRefreshFilter` logs `update_session` with `event.reason`
  `privilege_change`, the audit `session.id`, `user.name`, and the added and
  removed authority names, when the reloaded `ROLE_` authorities differ from
  the session's and the session is not already expired for revocation
  (revocation is logged by `SessionRevocationService` as `destroy_session`).
  It then saves the refreshed authentication to the session, so the change is
  logged once, not on every later request.

## Consequences

Idle-timeout expiry is logged when it is detected on the session's next use,
together with any other request carrying an unrecognized session ID; a
session that simply expires and is never used again still produces no event.
`resume_session` failures cannot be attributed to a user or correlated to the
earlier session's audit ID, and they are not rate-limited or aggregated here.
Their volume, with `source.ip`, is a detection input, not an alert.

A request with an unrecognized session ID now ends in the invalid-session
strategy instead of reaching authorization, so it produces `resume_session`
rather than an `authorize_access` denial. The same unauthenticated response
is now also given to such a request for the public `/oauth2/jwks` and
`/app/health` endpoints, and to a state-changing request whose session has
ended, which previously received the CSRF failure response. Callers of those
endpoints do not normally send the session cookie.

Session authorities are persisted when they change, so the session's stored
authentication tracks the local model instead of keeping the login-time
authorities; they are still reloaded on every request.
