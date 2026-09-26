# ADR 0022: Administrators cannot grant beyond their own roles

## Status

Accepted

## Context

The administration API has three management roles, each guarding one
resource family: `USER_MANAGE` for users, `GROUP_MANAGE` for groups, and
`ROLE_MANAGE` for roles. Their purpose is least privilege: an administrator
who maintains user accounts should not thereby control what the roles grant.

The service did not enforce that boundary. Because authorities are reloaded
on every request ([ADR 0015](0015-per-request-local-authority-refresh.md)),
each role alone could reach the other two on the holder's next request:

* A `USER_MANAGE` holder could add any user, including themselves, to the
  `Administrators` group.
* A `GROUP_MANAGE` holder could add `USER_MANAGE` to a group they belong to.
* A `ROLE_MANAGE` holder could rename `USER_MANAGE` to free the name, then
  rename a role they hold to `USER_MANAGE`, which also locked out every real
  holder. The same two renames work for any role name the application checks,
  reserved or not, because a role's name is its authority.

OWASP ASVS V8.2.1 asks for function-level access to be restricted to
consumers with explicit permissions, and the Authorization Cheat Sheet asks
for least privilege; a boundary any holder can cross meets neither.
Maker-checker, where a second administrator approves each change (ASVS
V2.3.5, level 3), was considered and left to adopters: it closes the same
paths but adds an approval workflow the template has no need for, while the
guards below are single-actor checks.

## Decision

`AdministrationService` rejects, with 403 and an `iam` failure event, any
change by an authenticated administrator that would:

* **Grant a role the administrator does not hold** (`event.reason`
  `exceeds_actor_privileges`): giving a user a group whose roles the
  administrator does not all hold, or giving a group a role the administrator
  does not hold. Removing access, and keeping a group's existing roles, is
  always allowed.
* **Change the administrator's own access** (`self_modification`): their own
  groups or enabled status, or deleting themselves. Their own display name
  and email address can still be changed.
* **Delete a reserved role** (`reserved_role`): `USER_MANAGE`,
  `GROUP_MANAGE`, or `ROLE_MANAGE`, which the administration API requires.

Role names are immutable: `PUT /admin/roles/{id}` is removed. A role created
with the wrong name is deleted, which is possible while no group holds it,
and created again.

The administrator's roles are the `ROLE_` authorities of the current request,
which `LocalAuthorityRefreshFilter` has just reloaded. A change the
application makes itself, with no authenticated user, is trusted.

No guard keeps at least one administrator. Two administrators can still
remove each other's access; the recovery is the first-administrator
bootstrap changeset described in the Authorization document.

## Consequences

Each management role can grant only what its holder already has, so no
single role reaches the others, and roles can no longer be renamed into
authorities. An administrator can still withdraw access they do not hold,
which is a denial-of-service risk the audit events from
[ADR 0021](0021-authorisation-change-audit-log-events.md) make visible, not a
privilege escalation.

The first administrator holds all three management roles and so can grant
any of them; delegating a narrower set means creating a group with only the
roles to delegate.

Clients that renamed roles must delete and recreate them instead.
