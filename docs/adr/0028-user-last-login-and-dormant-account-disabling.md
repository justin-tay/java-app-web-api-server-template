# ADR 0028: User last login and dormant account disabling

## Status

Accepted

## Context

Users have `enabled` but no record of when they last signed in. That blocks
two things. The admin user table cannot show when a user last signed in or
distinguish a user who has never signed in. And nothing can notify a user of a
sign-in after a long inactivity (OWASP ASVS 6.3.5) or disable accounts that
have gone unused.

OWASP ASVS and the Cheat Sheet Series do not require dormant accounts to be
disabled; some governance frameworks do. Session inactivity is a separate
matter, handled by the idle timeout in ADR 0006. Storing more per-user
sign-in data than needed adds personal data with no purpose: the client IP
and User-Agent are already captured in logs and session binding (ADR 0026),
and failed-attempt data belongs in logging.

## Decision

* **Stored data:** one nullable timestamp on the user, `lastLoginAt`, set on
  every successful sign-in, by OIDC or passkey, and null until the first. No
  IP address, User-Agent or failed-attempt count is stored on the user.
* **Pending status:** a user is `pending` when `enabled` is true and
  `lastLoginAt` is null, `disabled` when `enabled` is false, and `active`
  otherwise. It is derived, so no column or activation flow is added. The
  admin list exposes `status` as a filter (ADR 0027) and `lastLoginAt` as a
  field and sort property.
* **Dormant account disabling:** a scheduled job disables enabled users whose
  last sign-in (or `createdAt`, if never signed in) is older than
  `commons.accounts.dormancy.threshold`. It is off by default, since the
  threshold is a policy for adopters to set. Each disabled user has their
  sessions revoked through `SessionRevocationService`, and a dedicated audit
  event is logged. An administrator can re-enable the account.
* **Notification:** notifying users of a sign-in after long inactivity is not
  part of this decision; it needs a delivery channel the template lacks.

## Consequences

The table can show and sort by last sign-in, and an admin can find users who
never signed in. Sign-in gains one write per successful login, not per
request. Adopters who need dormancy controls turn on the job and choose the
threshold; those who delegate it to their identity provider leave it off.

Implementing the job lets the IM8 mapping for AC-3 move off "Not applicable"
when enabled, to be recorded in the IM8-specific docs only.
