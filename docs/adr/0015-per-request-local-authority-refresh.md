# ADR 0015: Per-request local authority refresh

## Status

Accepted

## Context

`LocalAuthoritiesOidcUserService` resolves a user's `ROLE_` authorities from
the local user, group, and role model only once, at login, and the result is
cached for the life of the session. Without a correction mechanism, a user
keeps acting under those cached authorities for as long as the session lives
(up to the 15-minute idle or 12-hour absolute timeout), even after an
administrator redefines a group's role set or deletes a role.

`SessionRevocationService` already closes part of this gap: it immediately
revokes a user's session when their account is disabled, deleted, or their
own group membership changes. It does not, and cannot practically, close all
of it: redefining a group's roles, or deleting a role, would require
enumerating every user in every affected group and revoking each of their
sessions, for every such change.

The alternative considered was re-deriving authorities from the database on
every request instead of caching them at all, which trades the small
correctness gap above for a guaranteed-fresh result. Sessions are already
JDBC-backed, meaning Spring Session already performs a database round trip
to load session state on every request; adding one more well-indexed,
single-user query alongside that existing round trip is a marginal cost, not
a new one.

## Decision

`LocalAuthorityRefreshFilter` reloads a user's `ROLE_` authorities from the
local user, group, and role model on every request, replacing the
`Authentication` in `SecurityContextHolder` before any authorization
decision is made. Non-`ROLE_` authorities (from the OIDC delegate) are left
untouched. A user who is missing or disabled at reload time is
deauthenticated immediately: their session is invalidated and the request is
treated as unauthenticated, the same way `AbsoluteSessionTimeoutFilter`
handles an expired session.

`SessionRevocationService` is kept, not replaced. Once authorities are
always freshly derived, its existing triggers (disable, delete, group
membership change) are redundant for correctness — the affected user's very
next request would reflect the change regardless. What it still buys is
promptness and an accurate audit trail: the session is marked expired the
instant an administrator acts, with a `session_destroyed` event recorded at
that moment, rather than only being discovered as a side effect of the
user's next request.

## Consequences

Every authenticated request now costs one additional query to resolve the
user's current groups and roles. Redefining a group's role set, or deleting
a role, takes effect on each affected member's next request without any
explicit revocation code needing to enumerate them. A future authorization
model change (for example, moving to attribute-based access control) should
extend `LocalAuthorityRefreshFilter`'s reload logic, not reintroduce
session-cached authorities.
