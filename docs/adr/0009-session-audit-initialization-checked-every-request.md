# ADR 0009: Session audit initialization checked on every request

## Status

Accepted

## Context

`SessionLifecycleAuditLogger`'s other three trigger points each map to a purpose-built
Spring Security extension point that fires exactly once, for exactly one cause:
`AbsoluteSessionTimeoutFilter` for timeout, `ContentNegotiatingSessionExpiredStrategy`
(a `SessionInformationExpiredStrategy`) for concurrent-session expiry,
`SessionLifecycleLogoutHandler` (a `LogoutHandler`) for logout, and an `@EventListener`
on `SessionFixationProtectionEvent` for renewal. Session *creation* has no equivalent
single event: a session can come into existence through login, CSRF token
establishment, session-fixation renewal, or any other code path that calls
`request.getSession(true)`, and no Spring Security or Spring Session event reliably
covers all of them. Tying initialization to one specific event risks missing a session
created some other way.

`logSessionCreatedIfNeeded` is already written to be idempotent (it returns immediately
once an audit identifier exists), which is what makes an unconditional per-request check
viable: the cost of calling it on every request, whether or not a session was just
created, is one cheap attribute read, and it is guaranteed to eventually stamp any
session by the next request that touches it, regardless of which path created it.

This check depended on nothing `AuthenticatedUserLoggingContextFilter` computes (only
`request.getSession(false)`), and lived there purely because that filter's `finally`
happened to wrap the right scope. Splitting it into its own filter, in the
`security.session` package alongside the other three trigger points instead of the
`logging` package, gives it a home consistent with its siblings and removes an
unrelated side effect from a filter whose name and Javadoc now describe only MDC
`user.name` handling.

## Decision

`SessionLifecycleAuditInitializationFilter` (`security.session` package) checks,
unconditionally on every request, whether the current session (if any) already has an
audit identifier, and stamps one via `SessionLifecycleAuditLogger.logSessionCreatedIfNeeded`
if not. It is not triggered by a specific "session created" event. It was extracted from
`AuthenticatedUserLoggingContextFilter` (formerly `SecurityLoggingContextFilter`), which
previously ran this same check from an unrelated `finally` block, alongside adding
`user.name` to MDC.

## Consequences

`SessionLifecycleAuditInitializationFilter` is registered
(`addFilterAfter(sessionLifecycleAuditInitializationFilter, AuthenticatedUserLoggingContextFilter.class)`
in `WebSecurityConfiguration`) immediately after `AuthenticatedUserLoggingContextFilter`,
so it still wraps the same downstream scope the check previously relied on. A future
session-lifecycle cause should follow the existing pattern: use a purpose-built Spring
Security extension point when one exists for that specific cause, and fall back to an
idempotent per-request check only when, as here, no single event covers every path that
can produce the condition being audited.
