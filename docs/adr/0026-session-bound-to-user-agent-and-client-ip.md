# ADR 0026: Bind sessions to User-Agent and detect client IP changes

## Status

Accepted

## Context

The OWASP Session Management Cheat Sheet recommends binding the session ID to
other client properties, such as IP address or User-Agent, to help detect
hijacking. [sessions.md](../system-design/08-crosscutting-concepts/02-security-and-authentication/sessions.md)
recorded this as "Not implemented", and separately warned against using either
property as an automatic invalidation criterion without a product decision,
since both change for reasons that do not indicate hijacking:

* A User-Agent is not expected to change mid-session: it identifies the same
  browser/OS combination that started the session, and a change usually means
  a different client is presenting the session cookie.
* A client IP address can legitimately change mid-session (a mobile carrier
  rotating addresses, Wi-Fi roaming, a corporate proxy failover), so treating
  every change as hijacking would log out legitimate users.

This asymmetry means the two properties need different responses, not one
shared "binding" behavior. A related, evaluated and rejected option was a
self-service "device activity" page modeled on Keycloak's account console.
The application enforces one concurrent session per user
([ADR 0006](0006-jdbc-backed-server-side-sessions.md)), so there is never more
than one session to list; the value of that feature was in the underlying
per-session client data, not in a multi-device view, so this ADR captures
that data without building the page.

## Decision

A new filter, `SessionBindingFilter`, runs in the same position as
`AbsoluteSessionTimeoutFilter`. On the first request where a session exists —
whether pre-login or already authenticated, and regardless of whether the
login went through OIDC or a passkey, since it only reads and writes
`HttpSession` attributes rather than anything OIDC-specific — it stores the
request's `User-Agent` header and the client IP resolved by the application's
`ClientIpResolver` bean (read from the `client.ip` logging context field that
`RequestCorrelationContextFilter` sets) as session attributes. On every later request it
compares the current value against the stored one:

* A **User-Agent mismatch invalidates the session**, logged as
  `destroy_session` with `session.termination_reason` `user_agent_mismatch`,
  gated by `commons.security.session.hijacking-protection` (default `true`).
* A **client IP mismatch only logs an anomaly**, reusing the `update_session`
  event with `event.reason` `client_ip_changed`, and updates the stored value
  so the same change is not logged again on every later request while the
  address keeps moving. Never invalidates the session. Gated by
  `commons.security.session.anomaly-detection` (default `true`).

Client IP detection reuses the application's existing `ClientIpResolver` bean
through that field, so it is also inactive when `commons.logging.enabled` is
false, and includes its `none()` default when no deployment has configured a trusted
resolver. When it resolves nothing, detection is silently inactive for that
request, the same way `client.ip` is already absent from request logs by
default; this feature does not require a resolver to be configured on its own.

Both flags are adopter-tunable, independent of each other, since some
deployments run infrastructure that legitimately rewrites the User-Agent
mid-session (for example some corporate proxies), which would need
`hijacking-protection` turned off without also giving up IP anomaly
detection.

## Consequences

A session hijacked by an attacker presenting a stolen session cookie from a
different browser is invalidated on their next request, without needing a
device list or any user-facing surface. A legitimate user roaming between
networks keeps their session; each IP change after the first produces one
`update_session` anomaly event rather than a flood, since the stored value
updates after each detected change.

No self-service endpoint, session list, or "device" concept is introduced.
Whether to surface this captured data to end users, or to build a broader
multi-device view, is a separate decision that would first need to reconsider
the single-concurrent-session policy in ADR 0006.

`sessions.md`'s "Binding the Session ID to Other User Properties" and
"Detecting Session ID Anomalies" rows move from "Not implemented" to reflect
this asymmetric implementation.
