# Authorization

Authorization converts an authenticated OpenID Connect (OIDC) identity into
local application permissions. Authentication is the responsibility of the
external identity provider; application authorisation is owned and enforced
by this application. The user, role, and permission data model this relies on,
and the components involved, are described in
[Building Block View](../../05-building-block-view.md#domain-model);
this covers how that model is enforced and assessed. The model follows the
NIST role-based access control (RBAC) model: a user holds roles and a role
holds permissions
([ADR 0038](../../../adr/0038-role-permission-model-and-account-review-classes.md)).

## Architectural principles

- Authorisation is independent of identity-provider realm and client roles.
- Access requires an active local user record; a suspended account is denied.
- Users hold roles, and roles hold permissions. A permission is the only thing
  the code checks, as a `domain:action` authority such as `user:create`.
- Granting access is privileged and withdrawing it is not, so an account that
  holds a privileged permission is a privileged account and is reviewed more
  often.
- Any role or permission change takes effect immediately, because authorities
  are reloaded on every request. A change to a user's own roles or account
  status also revokes their session, so they must sign in again.

## Identity resolution

The application resolves the claim named by the identity provider registration's
`spring.security.oauth2.client.provider.<id>.user-name-attribute` to the immutable
local `username` field. As in Spring Security, the attribute defaults to `sub`
when unset, so a registration whose local usernames are not the provider's
subject identifiers must name the claim that holds them (this application's
Keycloak registration sets `preferred_username`). The value may also be a dotted
path to a claim nested in an object, such as `xyz.preferred_username`; a claim
whose name is the whole value, such as a URL-style claim, is used as it is
before the value is read as a path. Spring Security reads the attribute only as
a top-level claim name, so `LocalAuthoritiesOidcUserService` resolves a nested
one itself and the principal's name is the resulting username.

Authentication succeeds only when a matching active local user exists. A
missing claim, an unknown local user, a suspended local user, or a local lookup
failure denies authentication. Successful authentication does not by itself
grant application permissions.

Keycloak realm and client roles are not translated into application
authorities.

## Authority resolution

At login, each permission of each of the user's roles is exposed as a Spring
Security authority named `domain:action`, with no prefix, so it is checked with
`hasAuthority()`. There is no `ROLE_` prefix and so no `hasRole()` case to avoid.
`LocalAuthorities` tells these authorities from the ones the login supplies
itself (`OIDC_USER`, `SCOPE_*` and `FACTOR_*`), which a refresh keeps.

The permissions are reference data that the `commons-accounts` schema seeds,
because its controllers check them by name, and the API cannot change them. The
`Permissions` class names each one, and a test fails if it and the seed differ.
Each permission has a `privileged` flag, which is also seed data.

| Domain | Actions (privileged marked **P**) |
| --- | --- |
| `application` | `access` |
| `user` | `read`, `create` **P**, `update`, `add-role` **P**, `remove-role`, `suspend`, `unsuspend` **P**, `remove`, `revoke-session`, `remove-passkey` |
| `role` | `read`, `create`, `update`, `delete`, `add-permission` **P**, `remove-permission` |
| `permission` | `read` |
| `settings` | `read`, `update` **P** |
| `audit` | `read` |
| `review` | `read`, `decide`, `confirm-population`, `download-report` |

Granting access is privileged and withdrawing it is not. `user:unsuspend` is
privileged because it restores access that a role still carries, which has the
effect of adding the role; `user:suspend` only removes access. `role:create` is
not privileged, because an empty role grants nothing, and `role:add-permission`
is the privileged step. Reading the settings is not privileged and changing
them is.

A user is **privileged** when any of their roles holds a privileged permission.
The status is computed from the user's roles and is not stored. It is a
read-only `privileged` field of the user responses, and it decides which account
review covers the account (see [Account review](#account-review)).

## Permission boundary

Each endpoint requires the permission for what it does, enforced by
`@PreAuthorize` on its controller method:

| Resource family | Permission |
| --- | --- |
| Reading users | `user:read` |
| Creating a user | `user:create` and `user:add-role` |
| Changing a user's name, email, or department | `user:update` |
| Giving a user a role | `user:add-role` |
| Taking a role from a user | `user:remove-role` |
| Suspending, unsuspending, removing a user | `user:suspend`, `user:unsuspend`, `user:remove` |
| Ending a user's sessions, removing their passkey | `user:revoke-session`, `user:remove-passkey` |
| Reading, creating, renaming, deleting a role | `role:read`, `role:create`, `role:update`, `role:delete` |
| Giving a role a permission, taking one away | `role:add-permission`, `role:remove-permission` |
| Reading the permissions | `permission:read` |
| Application settings | `settings:read`, `settings:update` |
| Audit trail | `audit:read` |
| Tasks, the account review and its report downloads | `review:read`, `review:decide`, `review:confirm-population`, `review:download-report` |

One request can need more than one permission, which the service checks: a
`PUT /admin/users/{id}` that adds a role needs `user:add-role`, one that removes a
role needs `user:remove-role`, and one that changes the name needs `user:update`.

The administration API is the sole mechanism for maintaining local users,
roles, and their relationships, once the first administrator exists
(see [Bootstrapping the first administrator](#bootstrapping-the-first-administrator)).

### Granting

An administration change by an authenticated user is rejected with 403 when it would:

* grant a privileged permission the user does not hold, by giving a user a role
  or a role a permission. Non-privileged permissions are reads, removals and
  review actions, so anyone allowed to grant may grant them, which lets the
  people who maintain accounts also maintain who reviews them; or
* change the user's own roles, or suspend, unsuspend, or remove them.

Nothing keeps at least one administrator: if the last holders of the privileged
permissions lose them, restore them with a changeset as for the first
administrator below. Roles can be renamed, because a role's name is only a
label; what a role grants is its permissions.

### Separation of duties

`app_permission_conflict` lists pairs of permissions that no user may hold
together, through one role or several. It is seeded with `review:decide` against
every privileged permission, so whoever reviews accounts cannot administer
them. This is NIST RBAC's static separation of duty (SP 800-53 AC-5).
It is checked when a role is given a permission and when a user is given a role,
against the user's combined permissions, and a breach is rejected with 409 and
the audit reason `separation_of_duties`. Role hierarchy and dynamic separation
of duty are not used.

Every change also needs a login no older than 15 minutes
([ADR 0023](../../../adr/0023-recent-login-for-administration-changes.md)). Requiring a
second administrator's approval for changes is left to adopters.

### Order of the checks

A request meets three checks, in this order:

1. **Domain gate.** `WebSecurityConfiguration` refuses with 403 a caller who holds no
   permission of the API's domain at all, such as no `user:*` permission for
   `/admin/users/**`.
2. **Recent login.** `AdminReauthenticationInterceptor` answers a change from a login
   older than 15 minutes with the `reauthentication-required` problem.
3. **Permission.** `@PreAuthorize` and the service check the permission the endpoint
   and the change need.

The gate comes first so that someone who may not use an API is never asked to sign in
again to use it, only to be refused afterwards. The gate is coarse on purpose, and has
no list of permissions to keep in step with the code: a caller who holds, say, only
`role:read` passes it for `/admin/roles/**`, is asked for a recent login before a
change, and is then refused by the permission check.

The inactivity job can remove the last holder of the privileged permissions
without anyone acting: it removes any account not in use for the removal
threshold, 180 days by default, including the only administrator who has not
signed in for that long
([ADR 0031](../../../adr/0031-inactive-account-suspension-and-removal.md)). The job has no
exemption for the last administrator, so recovery is the first-administrator
changeset below, and a deployment that cannot accept that risk turns the job off
or lengthens the threshold in the settings.

## Account review

An account that holds a privileged permission is reviewed in the privileged
account review, monthly by default, and every other account in the
non-privileged account review, yearly by default
([ADR 0038](../../../adr/0038-role-permission-model-and-account-review-classes.md)). The two intervals
are the settings `review.privilegedIntervalMonths` and
`review.nonPrivilegedIntervalMonths`, each 1, 3, 6 or 12, and the
non-privileged interval cannot be shorter than the privileged one. The terms
follow NIST SP 800-53 (AC-2(7), AC-6(5), AC-6(7)), which leaves the frequency to
the organization.

A reviewer removes access and never grants it. Removing an account needs
`review:decide` and `user:remove`, and removing a role from an account in the
review needs `review:decide` and `user:remove-role`. The seeded
`Account Reviewers` role holds no permission that adds. It also holds no
privileged permission, so a reviewer is a non-privileged account, and a reviewer
cannot act on their own account.

## Bootstrapping the first administrator

The `commons-accounts` schema seeds the permissions and the pairs that conflict.
Liquibase applies the roles `Administrators`, which holds every permission except
the `review` ones, and `Account Reviewers`, in every environment
(`reference-data.sql`). The local users `admin`, `user`, `multi-group-user`,
`account-reviewer-1`, and `account-reviewer-2`, and the `Users` role, are
development and test fixtures (`development-seed.sql`): Liquibase applies them
only when the `dev` context is explicitly requested, which the `local` and `test`
profiles and `bin/start-api-server-tls.sh` do. A production migration must not
request the `dev` context, so it creates no local user
([ADR 0018](../../../adr/0018-development-fixtures-kept-out-of-production.md)).

A new production database therefore has no user who can call the
administration API. Create the first administrator once, as a data change
applied by the migration job like every other
([ADR 0004](../../../adr/0004-database-schema-management.md)): add a changeset
that inserts the local user and its `Administrators` role, restricted
to the environments that should have it with a required context (for
example `context:@production`) that only those migration runs request.

```sql
INSERT INTO app_user (id, public_id, username, name, status, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by)
VALUES (<unused id below 900>, '<new UUID>', '<Keycloak preferred_username>', '<name>', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');
INSERT INTO app_user_role (user_id, role_id)
VALUES (<the same id>, 17);
```

The `username` must equal the `preferred_username` of an existing Keycloak
account whose username is immutable (see
[Identity resolution](#identity-resolution)). That administrator then
maintains every further user, role, and role membership through the
administration API.

## Security behaviour

`LocalAuthorityRefreshFilter` reloads a user's local authorities from the
local user, role, and permission model on every request, rather than trusting the
authorities computed once at login. Any authorization-relevant change,
including removing a permission from a role or deleting a role, takes effect on
the affected user's very next request, not just at their next login.

A local user who has been suspended or removed since login is deauthenticated
immediately by the same filter: its session is invalidated and the request is
treated as unauthenticated. `SessionRevocationService` additionally revokes a
user's session as soon as an administrator disables their account, deletes
it, or changes their roles, so those specific changes take effect
promptly, with an accurate audit record, rather than only on the user's next
request.

This approach separates authentication from application authorisation, keeps
access grants aligned with organisational responsibilities, and lets
administrators manage application access without managing identity-provider
roles.

## OWASP control implementation

The application's authorization posture is recorded here against the
[OWASP Authorization Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

### Status meanings

<!-- ocsv:generated source="cheatsheets/Authorization_Cheat_Sheet.md" source-ref="0b43888" code-ref="26770b1" -->
| Status | Meaning |
| --- | --- |
| Implemented | The application's own code or configuration fulfils the recommendation. |
| Partial | Part of the recommendation is met; the row says which part and why the rest is not. |
| Alternative | A deliberate, documented approach used instead of the recommended one; the row says what it gives up compared with the recommendation, so an adopter can decide whether that is acceptable for their system. |
| Not applicable | The application has no capability or mechanism the recommendation addresses. Reassess before adding one. |
<!-- /ocsv:generated -->

### Recommendations

<!-- ocsv:generated source="cheatsheets/Authorization_Cheat_Sheet.md" source-ref="0b43888" code-ref="26770b1" -->
| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| **Enforce Least Privileges** | Implemented | **Vertically (role-based):** each administration endpoint requires the specific permission for what it does, not a general "admin" grant: `user:read` to list users, `user:create` to create one, `role:add-permission` to give a role a permission, and so on (see [Permission boundary](#permission-boundary)). Granting access is privileged and withdrawing it is not, a user can grant only the privileged permissions they hold, cannot change their own access, and cannot hold reviewing together with a privileged permission (see [Granting](#granting) and [Separation of duties](#separation-of-duties)).<br><br>**Horizontally (per-owner/per-tenant):** not applicable today. There is no per-owner or per-tenant resource boundary to separate, because the template has no multi-tenancy; any holder of a permission applies it to the entire corresponding collection by design. A permission has no instance part, as in Apache Shiro's `domain:action:instance`, because there is nothing for one to name; an adopter that introduces multi-tenancy or per-user resource ownership must add its own horizontal checks.<br><br>**Application code:** `@PreAuthorize` on `UserAdminController`, `RoleAdminController`, `PermissionAdminController`, `SettingsController`, `AuditEventController`, `AccountReviewController`, `TaskController`, and the checks in `AdministrationService` and `AccountReviewService`; **Test code:** `AdministrationServiceTest`, `AdminApiIntegrationTest.eachEndpointAdmitsOnlyThePermissionItNeeds()`, `AdminApiIntegrationTest.anAdministratorCannotChangeTheirOwnAccessOrBreakSeparationOfDuties()`; **Decision:** [ADR 0038](../../../adr/0038-role-permission-model-and-account-review-classes.md). |
| **Deny by Default** | Implemented | Every request not explicitly permitted requires authentication (`.requestMatchers("/**").authenticated()`), and every administration endpoint additionally requires its specific permission by `@PreAuthorize`; nothing is reachable by an unauthenticated or under-privileged request unless a rule explicitly allows it. The permissions are seeded and no role holds one by default except through the seeded roles, so an application that seeds none denies every administration request.<br><br>**Application code:** `WebSecurityConfiguration.securityFilterChain()` and the controllers' `@PreAuthorize`. |
| **Validate the Permissions on Every Request** | Implemented | Authorization is not decided once and cached: `@PreAuthorize`/`authorizeHttpRequests` re-evaluate on every request, and the user's permission authorities themselves are reloaded from the database on every request rather than trusted from login, so a role or permission change also takes effect immediately. Confirm against the deployed service that a role's permission set change or a user's own role membership or enabled-status change takes effect on that user's very next request, without requiring re-login.<br><br>**Application code:** `LocalAuthorityRefreshFilter`; **Test code:** `LocalAuthorityRefreshFilterTest`; **Framework default:** Spring Security method security and `authorizeHttpRequests`; **Decision:** [ADR 0015](../../../adr/0015-per-request-local-authority-refresh.md). |
| **Thoroughly Review the Authorization Logic of Chosen Tools and Technologies, Implementing Custom Logic if Necessary** | Partial | **Misconfiguration:** an authority is the permission's `domain:action` with no `ROLE_` prefix, checked with `hasAuthority()`, so the prefix handling of `hasRole()`, which differs between the Java configuration and a `@PreAuthorize` expression, does not apply. `LocalAuthorities` tells the local authorities from the ones the login supplies, by name.<br><br>**Vulnerable components:** not implemented. The build (`pom.xml`) and CI workflow (`build-and-test.yml`) run no dependency vulnerability scan such as OWASP Dependency-Check, and the repository defines no process for detecting and responding to vulnerable components.<br><br>**Application code:** `LocalAuthorities`, `AppUserLocalAuthorityLookup`. |
| **Prefer Attribute and Relationship Based Access Control over RBAC** | Alternative | The template uses a role-based model after NIST RBAC (user, role, permission, with static separation of duties; see [Building Block View](../../05-building-block-view.md#domain-model)) instead of ABAC or ReBAC, as a deliberate simplicity choice for a single-tenant administration model. Compared with the recommendation, it gives up object-level, relationship-, and attribute-based decisions; nothing else in the template supplies them. An adopter needing that granularity must extend or replace this model, for example with an instance part of a permission.<br><br>**Application code:** `AppUser`/`AppRole`/`AppPermission` and `LocalAuthoritiesOidcUserService`; **Decision:** [ADR 0038](../../../adr/0038-role-permission-model-and-account-review-classes.md). |
| **Ensure Lookup IDs are Not Accessible Even When Guessed or Cannot Be Tampered With** | Implemented | Every identifier the API exposes is a randomly generated UUID (`AbstractIdentifiedEntity.publicId`), not a sequential or otherwise guessable value, and the sequence primary key is never exposed, and every per-object lookup (`AdministrationService.user()`/`role()`/`permission()`) is reached only after the permission check of its endpoint. There is no per-object ownership check beyond that permission, because the template has no concept of one user "owning" a subset of another user's resources; see the least-privilege row above for the multi-tenant caveat.<br><br>**Application code:** `AbstractIdentifiedEntity` (UUID generation), `AdministrationService`. |
| **Enforce Authorization Checks on Static Resources** | Not applicable | The application serves no static resources or files of any kind; there is no `WebMvcConfigurer` resource handler, no `app-web-api-server/src/main/resources/static` or `public` directory, and no bundled frontend. Reassess if static content is ever added. |
| **Verify that Authorization Checks are Performed in the Right Location** | Implemented | All authorization is enforced by the Spring Security filter chain and `@PreAuthorize` method security running on the server; the application has no client-side code that could perform or be relied on for an authorization decision.<br><br>**Application code:** `WebSecurityConfiguration.securityFilterChain()`, `@PreAuthorize` on the administration controllers; **Framework default:** Spring Security `authorizeHttpRequests` and method security. |
| **Exit Safely when Authorization Checks Fail** | Implemented | Access-denied and CSRF failures are handled by one centralized handler that returns a generic RFC 9457 Problem Details response with no internal detail; a missing or conflicting resource is likewise handled centrally rather than by ad hoc per-endpoint logic (see [Error responses](error-responses.md)). A `@PreAuthorize` denial raised inside a controller reaches the same handler: `ApiResponseEntityExceptionHandler` rethrows `AccessDeniedException` to Spring Security instead of answering it as a 500 through its catch-all handler, and the denial is logged as `authorize_access`. Confirm access-denied and CSRF-failure responses never disclose which specific check failed or any internal detail.<br><br>**Application code:** `ProblemDetailAccessDeniedHandler`, `ApiResponseEntityExceptionHandler.rethrowAccessDenied()`; **Test code:** `ProblemDetailAccessDeniedHandlerTest`, `AdminControllerTest.userListRequiresUserManagementRole()`, `MethodSecurityAccessDeniedIntegrationTest.methodSecurityDenialReturnsAccessDeniedProblemDetailAndIsAudited()`. |
| **Implement Appropriate Logging** | Implemented | Authorization denials are recorded as structured ECS audit events (`event.category=[web, api]`, `event.type=[access, denied]`, `event.action=authorize_access`, `event.outcome=failure`), the same mechanism used for authentication and CSRF audit events (see [Logging](../06-logging-and-monitoring/README.md)). Confirm these events are recorded without credentials or tokens.<br><br>**Application code:** `SecurityAuditEventLogger.onAuthorizationDenied()`; **Test code:** `SecurityAuditEventLoggerTest.logsAuthorizationDenialForAnAnonymousUser()`. |
| **Create Unit and Integration Test Cases for Authorization Logic** | Implemented | `AdminApiIntegrationTest.eachEndpointAdmitsOnlyThePermissionItNeeds()` checks each administration endpoint against permissions it needs and ones it does not, and `writesNeedTheirOwnPermissionToo()` shows a write needs its own permission. `AdministrationServiceTest` covers granting only the privileged permissions you hold, the permissions a change needs, and separation of duties. `AnonymousAccessIntegrationTest` shows every controller path rejects an unauthenticated request unless it is explicitly allowed, and that an unmapped path is denied rather than routed. `LocalAuthorityRefreshFilterTest` unit-tests the per-request reload of authorities and the deauthentication of a disabled or deleted user.<br><br>**Test code:** `AdminApiIntegrationTest`, `AdministrationServiceTest`, `AdminControllerTest.userListRequiresTheUserReadPermission()`, `AnonymousAccessIntegrationTest`, `LocalAuthorityRefreshFilterTest`. |
<!-- /ocsv:generated -->

Related documentation: [Building Block View](../../05-building-block-view.md#contained-building-blocks),
[Authentication](authentication.md),
[Sessions](sessions.md), [Logging](../06-logging-and-monitoring/README.md), and
[ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md).
