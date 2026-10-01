# ADR 0033: User status is the lifecycle status only

## Status

Accepted

## Context

[ADR 0028](0028-user-last-login-and-dormant-account-disabling.md) derived a three-value
`status` for the administration API: `active` (signed in), `suspended`, and `pending`
(active, never signed in). The account lifecycle has two states, `active` and `suspended`
([ADR 0031](0031-inactive-account-suspension-and-removal.md)), and `pending` is only a name
for an active account with no `lastLoginAt`.

The derived value made the API disagree with itself. A never-signed-in account is active,
but the `status=active` filter and the active count excluded it. The word also collides
with the review status `pending_verification` ([ADR 0032](0032-periodic-account-review.md)).

## Decision

* **Status:** `UserResponse.status` is `active` or `suspended`, the stored `AccountStatus`.
  The derived `UserStatus` enum is removed.
* **Never signed in:** the client reads it from a null `lastLoginAt`. No response field is
  added, since a client can derive it.
* **Filters:** `status` accepts `active` or `suspended`, and `active` includes accounts
  that never signed in. `status=pending` is a `400`. A new `neverSignedIn` filter selects
  accounts with no `lastLoginAt` when `true` and accounts with one when `false`. It
  combines with `status` by AND, so `status=suspended&neverSignedIn=true` is allowed.
* **No deprecation period:** the only client is the template's own frontend.

## Consequences

The label and the count agree, and the lifecycle has one vocabulary. A client that sent
`status=pending` must send `neverSignedIn=true` instead.

This supersedes the pending status decision of ADR 0028. Its `lastLoginAt` stays.
