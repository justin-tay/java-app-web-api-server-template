# Security Authorization

## Purpose and scope

This document describes how the application converts an authenticated OpenID
Connect (OIDC) identity into local application permissions. Authentication is
the responsibility of the external identity provider; application
authorisation is owned and enforced by this application.

## Architectural principles

- Authorisation is independent of identity-provider realm and client roles.
- Access requires an enabled local user record.
- Permissions are granted through organisational groups.
- Users cannot receive roles directly.
- Role and membership changes take effect when the user next authenticates.

## Authorisation model

```text
User  * ── * Group  * ── * Role  ──> Spring Security authority
```

A user may belong to one or more groups. A group represents an organisational
position or responsibility and contains the roles required for that position.
A user's effective permissions are the union of the roles assigned to every
group to which they belong.

The model deliberately does not support direct user-to-role grants. This keeps
permissions understandable through group membership and prevents exceptions
from becoming an alternative access-control mechanism.

## Identity resolution

The application resolves the OIDC `preferred_username` claim to the immutable
local `username` field.

Authentication succeeds only when a matching enabled local user exists. A
missing claim, an unknown local user, a disabled local user, or a local lookup
failure denies authentication. Successful authentication does not by itself
grant application permissions.

Keycloak realm and client roles are not translated into application
authorities.

## Authority resolution

Roles are stored without the Spring Security prefix. At login, each effective
local role is exposed as a Spring Security authority by prepending `ROLE_`.

| Stored role | Spring Security authority | Purpose |
| --- | --- | --- |
| `USER_MANAGE` | `ROLE_USER_MANAGE` | Manage local users |
| `GROUP_MANAGE` | `ROLE_GROUP_MANAGE` | Manage groups |
| `ROLE_MANAGE` | `ROLE_ROLE_MANAGE` | Manage roles |
| `APPLICATION_USER` | `ROLE_APPLICATION_USER` | Baseline application access |

`ROLE_MANAGE` is checked with `hasAuthority("ROLE_ROLE_MANAGE")`. It must not
be checked with `hasRole`, because `hasRole` adds the `ROLE_` prefix and the
stored role name already begins with `ROLE`.

## Component responsibilities

| Component | Responsibility |
| --- | --- |
| Identity provider | Authenticates users and provides OIDC identity claims. |
| Local OIDC user service | Resolves the local user and derives effective authorities. |
| Local user, group, and role model | Stores organisational membership and role grants. |
| Spring Security | Enforces resolved authorities at the application boundary. |
| Administration API | Maintains the local authorisation model. |

## Data model and invariants

`AppUser` and `AppGroup` have a many-to-many relationship. A user must belong
to at least one group. `AppGroup` and `AppRole` also have a many-to-many
relationship.

The model preserves these invariants:

- Local usernames are immutable after creation.
- Deleting a user removes its group memberships.
- A group with users cannot be deleted.
- A role assigned to a group cannot be deleted.
- A role can be assigned to multiple groups and is granted once even if several
  memberships provide it.

## Management authority boundary

| Resource family | Required authority |
| --- | --- |
| User administration | `ROLE_USER_MANAGE` |
| Group administration | `ROLE_GROUP_MANAGE` |
| Role administration | `ROLE_ROLE_MANAGE` |

The administration API is the sole mechanism for maintaining local users,
groups, roles, and their relationships.

## Security behaviour

Authorities are loaded at login and remain associated with the authenticated
session. A change to group membership or group roles affects a user at their
next authentication event; it does not retroactively change authorities in an
existing session.

This approach separates authentication from application authorisation, keeps
access grants aligned with organisational responsibilities, and lets
administrators manage application access without managing identity-provider
roles.
