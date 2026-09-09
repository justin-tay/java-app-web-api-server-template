# Requirements: Local User Authorisation

## Overview

The application needs a local authorisation model alongside existing Keycloak OIDC
authentication. It maintains users, organisational groups, and roles. Users are
created locally before login, can belong to one or more groups, and gain local
roles through all their groups. Keycloak remains responsible for authentication,
credentials, and its user records.

## Terminology

- **Local user:** An application record for a person authenticated by Keycloak.
- **Username:** The local user identifier. It must equal Keycloak's unique,
  non-user-editable OIDC `preferred_username` claim.
- **Group:** An organisational-position grouping; a user can hold several.
- **Role:** A reusable application permission granted through a group.
- **Administrator:** A user whose roles permit one or more management APIs.

## User roles

- **Unauthorised authenticated user:** A Keycloak-authenticated person without an
  enabled local user record; they cannot use the application.
- **Application user:** An enabled local user who receives roles from their groups.
- **Administrator:** An application user with a management role.

## Requirements

### R1: Local-user identity and lifecycle

**User story:** As an administrator, I want to pre-provision and manage local
users, so that only known people can access the application after Keycloak login.

1. WHEN an administrator creates a local user with a username, THEN the system
   SHALL persist the username used by the matching Keycloak
   `preferred_username` claim.
2. WHEN an administrator creates or updates a local user, THEN the system SHALL
   require at least one assigned group.
3. WHEN a duplicate username is submitted, THEN the system SHALL reject it as a
   conflict.
4. WHEN an administrator changes a user's groups, THEN the system SHALL replace
   its group memberships with the supplied valid group set.
5. WHEN an administrator enables or disables a user, THEN the system SHALL persist
   the new lifecycle state.
6. WHEN an administrator deletes a local user, THEN the system SHALL delete only
   the local record and SHALL NOT modify the Keycloak account.
7. WHEN OIDC login succeeds and an enabled local user whose username equals the
   OIDC `preferred_username` exists, THEN the system SHALL permit application
   access subject to the user's granted roles.
8. WHEN OIDC login succeeds and no matching enabled local user exists, THEN the
   system SHALL reject the login and deny application access.

### R2: Organisational groups and memberships

**User story:** As an administrator, I want to manage groups that represent
organisational positions and assign users to them, so that access follows a
person's positions.

1. WHEN an administrator creates a group with a unique name, THEN the system
   SHALL persist the group.
2. WHEN an administrator creates or updates a group, THEN the system SHALL allow
   zero or more assigned roles.
3. WHEN an administrator requests a group, THEN the system SHALL return its
   members and assigned roles.
4. WHEN an administrator deletes a group that has one or more users, THEN the
   system SHALL reject deletion as a conflict.
5. WHEN an administrator deletes an empty group, THEN the system SHALL remove its
   role assignments and delete the group.
6. WHEN a group name duplicates an existing group name, THEN the system SHALL
   reject the request as a conflict.

### R3: Roles and authorisation grants

**User story:** As an administrator, I want to define reusable roles and grant
them through groups, so that permissions are administered consistently.

1. WHEN an administrator creates a role with a unique name, THEN the system SHALL
   persist the role.
2. WHEN an administrator assigns roles to a group, THEN the system SHALL replace
   the group's role assignments with the supplied valid role set.
3. WHEN a user belongs to multiple groups, THEN the system SHALL grant the union
   of all roles assigned to those groups.
4. WHEN an administrator attempts to delete a role assigned to one or more groups,
   THEN the system SHALL reject deletion as a conflict.
5. WHEN a role name duplicates an existing role name, THEN the system SHALL reject
   the request as a conflict.
6. WHEN management roles are configured, THEN the system SHALL use them to protect
   the corresponding user, group, and role management APIs.
7. WHEN management roles are seeded, THEN the system SHALL seed `USER_MANAGE`,
   `GROUP_MANAGE`, `ROLE_MANAGE`, and `APPLICATION_USER`; their Spring Security
   authorities SHALL be `ROLE_USER_MANAGE`, `ROLE_GROUP_MANAGE`,
   `ROLE_ROLE_MANAGE`, and `ROLE_APPLICATION_USER`, respectively.

### R4: Spring Security authority enrichment

**User story:** As an application user, I want roles from all my organisational
groups added to my Spring Security authorities at login, so that authorisation
reflects my local position assignments.

1. WHEN an enabled local user completes OIDC login, THEN the system SHALL load the
   user's groups and all roles assigned to those groups.
2. WHEN local roles are loaded at login, THEN the system SHALL add every distinct
   role as a Spring Security granted authority.
3. WHEN user memberships or group roles change, THEN the system SHALL apply the
   change at that user's next login.
4. WHEN local authorisation data cannot be loaded reliably during login, THEN the
   system SHALL deny application access rather than grant indeterminate authority.
5. WHEN Keycloak supplies realm or client roles, THEN the system SHALL NOT use
   those roles as application authorities; local group-derived roles are the sole
   source of application roles.

### R5: Administration CRUD API

**User story:** As an administrator, I want protected APIs for users, groups, and
roles, so that clients can manage application access.

1. WHEN an authorised administrator submits valid user, group, or role data, THEN
   the system SHALL create, retrieve, update, list, or delete the resource.
2. WHEN a caller lacks the role required for a management operation, THEN the
   system SHALL deny that operation.
3. IF a requested resource does not exist, THEN the system SHALL return `404 Not
   Found`.
4. IF request data is malformed, incomplete, or refers to an unknown group or
   role, THEN the system SHALL return `400 Bad Request` or `404 Not Found` as
   applicable.
5. WHEN request data fails syntactic validation, THEN the system SHALL return a
   `400 Bad Request` problem-detail response containing a safe field-level error
   list with each error's JSON Pointer source path, validation code, and
   user-facing message; errors that cannot be attributed to one request field
   SHALL omit the source path and SHALL be presented as global form errors.
6. WHEN a request violates a uniqueness or referenced-resource deletion rule,
   THEN the system SHALL return `409 Conflict`.
7. WHEN a resource is created, updated, or deleted successfully, THEN the system
   SHALL return `201 Created`, `200 OK`, or `204 No Content`, respectively.
8. WHEN an authorised administrator lists users, groups, or roles, THEN the system
   SHALL return offset-paginated results using zero-based `page` and `size` query
   parameters.
9. WHEN a list request omits pagination parameters, THEN the system SHALL return
   the first page with 20 items; WHEN `size` exceeds 100, THEN the system SHALL
   reject the request as invalid.
10. WHEN the system returns a list page, THEN it SHALL include the items, current
   page number, page size, total item count, and total page count.
11. WHEN an authorised administrator supplies a valid `sort` parameter, THEN the
    system SHALL sort the list deterministically; when omitted, users SHALL sort
    by username and groups and roles SHALL sort by name, ascending.
12. WHEN an authorised administrator lists users, THEN the system SHALL support
    filters for username, display name, enabled status, and group.
13. WHEN an authorised administrator lists groups, THEN the system SHALL support
    filters for name and role; WHEN listing roles, THEN the system SHALL support
    filtering by name.
14. WHEN a name-based filter is applied, THEN the system SHALL match names without
    regard to letter case.
15. WHEN an authorised administrator requests a list, THEN the system SHALL use
    offset pagination only; cursor pagination is out of scope for this feature.

### R6: Database schema and initial data

**User story:** As an operator, I want Liquibase to provide the schema and
realistic test fixtures, so that environments are reproducible.

1. WHEN the application starts with an empty database, THEN Liquibase SHALL create
   local-user, group, role, user-group membership, and group-role assignment
   structures.
2. WHEN Liquibase creates the schema, THEN it SHALL enforce unique user, group,
   and role names and prevent duplicate membership or role-assignment links.
3. WHEN Liquibase applies initial data, THEN it SHALL create the `USER_MANAGE`,
   `GROUP_MANAGE`, `ROLE_MANAGE`, and `APPLICATION_USER` roles; the
   `Administrators` and `Test Users` groups; and enabled `admin`, `test-user`,
   and `multi-group-user` test users.
4. WHEN initial data is applied, THEN it SHALL associate the administrator test
   user with `USER_MANAGE`, `GROUP_MANAGE`, and `ROLE_MANAGE` through its group
   memberships.
5. WHEN initial data is applied, THEN it SHALL assign `APPLICATION_USER` to the
   `Test Users` group and SHALL give `multi-group-user` memberships in both seeded
   groups.
6. WHEN migrations run more than once, THEN Liquibase SHALL not reapply recorded
   changesets.
7. WHEN schema and data are expressed, THEN they SHALL use Liquibase formatted SQL
   and avoid database-vendor-specific DDL, identity/UUID generation, upsert syntax,
   and proprietary types.
8. WHEN Keycloak test identities are provisioned, THEN a separate seed script
   SHALL create `admin`, `test-user`, and `multi-group-user` with password
   `password`, and SHALL not assign Keycloak roles to them.

## Required local-user data

- Internal identifier
- Username / Keycloak `preferred_username`
- Display name
- Enabled or disabled lifecycle state
- One or more assigned groups
- Created and last-updated timestamps

An email address is optional. Lifecycle-change reasons and lifecycle-history data
are out of scope.

## Constraints and edge cases

- The system must not store passwords or manage Keycloak credentials.
- Test-only Keycloak identity provisioning is separate from Liquibase and does not
  grant Keycloak roles; application roles exist only in the local application
  database.
- A disabled user is denied despite a valid Keycloak login.
- An unknown or disabled local user is rejected during OIDC login rather than
  receiving an authenticated session with no local access.
- Keycloak `preferred_username` must be unique and non-user-editable; altering it
  would otherwise break the local-user mapping.
- An update cannot leave an enabled user without a group.
- A duplicate role received through multiple groups is effective only once.
- Direct user-to-role assignment, nested groups, role inheritance, audit history,
  approval workflows, and cursor pagination are out of scope.
