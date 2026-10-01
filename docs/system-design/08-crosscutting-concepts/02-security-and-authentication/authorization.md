# Authorization

Authorization converts an authenticated OpenID Connect (OIDC) identity into
local application permissions. Authentication is the responsibility of the
external identity provider; application authorisation is owned and enforced
by this application. The user/group/role data model this relies on, and the
components involved, are described in
[Building Block View](../../05-building-block-view.md#domain-model);
this covers how that model is enforced and assessed.

## Architectural principles

- Authorisation is independent of identity-provider realm and client roles.
- Access requires an enabled local user record.
- Permissions are granted through organisational groups.
- Users cannot receive roles directly.
- Any role or group change takes effect immediately, because authorities
  are reloaded on every request. A change to a user's own group membership
  or account status also revokes their session, so they must sign in again.

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

`ROLE_MANAGE` is checked with `hasAuthority("ROLE_ROLE_MANAGE")`. It cannot
be checked with `hasRole`, because the stored role name already begins with
`ROLE_`. In the security filter chain's Java configuration, Spring Security's
`hasRole()` rejects any argument that starts with `ROLE_` with an
`IllegalArgumentException`. In a `@PreAuthorize` expression, `hasRole()`
strips one leading `ROLE_` before prepending it again, so
`hasRole('ROLE_MANAGE')` checks the wrong authority (`ROLE_MANAGE`) and
`hasRole('ROLE_ROLE_MANAGE')` is rejected.

## Management authority boundary

| Resource family | Required authority |
| --- | --- |
| User administration | `ROLE_USER_MANAGE` |
| Group administration | `ROLE_GROUP_MANAGE` |
| Role administration | `ROLE_ROLE_MANAGE` |

The administration API is the sole mechanism for maintaining local users,
groups, roles, and their relationships, once the first administrator exists
(see [Bootstrapping the first administrator](#bootstrapping-the-first-administrator)).

Each management authority stays within its family because an administrator
cannot grant more than they hold
([ADR 0022](../../../adr/0022-administrators-cannot-grant-beyond-their-own-roles.md)). An administration
change by an authenticated administrator is rejected with 403 when it would:

* grant a role the administrator does not hold, by giving a user a group or a
  group a role;
* change the administrator's own groups or enabled status, or delete them; or
* delete `USER_MANAGE`, `GROUP_MANAGE`, or `ROLE_MANAGE`, which the API requires.

Role names cannot be changed, because a role's name is the authority the
application checks: there is no `PUT /admin/roles/{id}`. Without these rules,
each management role could reach the other two: by adding its holder to
`Administrators`, by adding `USER_MANAGE` to its holder's group, or by
renaming a role its holder has to a management role's name.

Every change also needs a login no older than 15 minutes
([ADR 0023](../../../adr/0023-recent-login-for-administration-changes.md)). Nothing keeps at
least one administrator: if the last holders of a management role lose it,
restore it with a changeset as for the first administrator below. Requiring a
second administrator's approval for changes is left to adopters.

## Bootstrapping the first administrator

Liquibase applies the four roles above and the `Administrators` group, which
holds the three management roles, in every environment
(`002-authorisation-seed.sql`). The local users `admin`, `test-user`, and
`multi-group-user`, and the `Test Users` group, are development and test
fixtures (`004-development-seed.sql`): Liquibase applies them only when the
`dev` context is explicitly requested, which the `local` and `test` profiles
and `bin/start-api-server-tls.sh` do. A production migration must not request
the `dev` context, so it creates no local user
([ADR 0018](../../../adr/0018-development-fixtures-kept-out-of-production.md)).

A new production database therefore has no user who can call the
administration API. Create the first administrator once, as a data change
applied by the migration job like every other
([ADR 0004](../../../adr/0004-database-schema-management.md)): add a changeset
that inserts the local user and its `Administrators` membership, restricted
to the environments that should have it with a required context (for
example `context:@production`) that only those migration runs request.

```sql
INSERT INTO app_user (id, username, name, enabled, created_at, updated_at, created_by, updated_by)
VALUES ('<new UUID>', '<Keycloak preferred_username>', '<name>', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');
INSERT INTO app_user_group (user_id, group_id)
VALUES ('<the same UUID>', '00000000-0000-0000-0000-000000000011');
```

The `username` must equal the `preferred_username` of an existing Keycloak
account whose username is immutable (see
[Identity resolution](#identity-resolution)). That administrator then
maintains every further user, group, and membership through the
administration API.

## Security behaviour

`LocalAuthorityRefreshFilter` reloads a user's `ROLE_` authorities from the
local user, group, and role model on every request, rather than trusting the
authorities computed once at login. Any authorization-relevant change,
including removing a role from a group or deleting a role, takes effect on
the affected user's very next request, not just at their next login.

A local user who has been disabled or deleted since login is deauthenticated
immediately by the same filter: its session is invalidated and the request is
treated as unauthenticated. `SessionRevocationService` additionally revokes a
user's session as soon as an administrator disables their account, deletes
it, or changes their group membership, so those specific changes take effect
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
| **Enforce Least Privileges** | Implemented | **Vertically (role-based):** each administration path requires the specific management authority for that resource family, not a general "admin" grant: `/admin/users/**` requires `ROLE_USER_MANAGE`, `/admin/groups/**` requires `ROLE_GROUP_MANAGE`, `/admin/roles/**` requires `ROLE_ROLE_MANAGE`. Within a family, an administrator cannot grant a role they do not hold, change their own access, or delete a reserved role, so no management authority can reach another (see [Management authority boundary](#management-authority-boundary)).<br><br>**Horizontally (per-owner/per-tenant):** not applicable today. There is no per-owner or per-tenant resource boundary to separate, because the template has no multi-tenancy; any holder of a management role administers the entire corresponding collection by design. An adopter that introduces multi-tenancy or per-user resource ownership must add its own horizontal checks.<br><br>**Application code:** `WebSecurityConfiguration.securityFilterChain()` (`.hasRole("USER_MANAGE")`, `.hasRole("GROUP_MANAGE")`, `.hasAuthority("ROLE_ROLE_MANAGE")`), `@PreAuthorize` on `UserAdminController`, `GroupAdminController`, `RoleAdminController`, `AdministrationService`; **Test code:** `AdministrationServiceTest`, `AdminApiIntegrationTest.anAdministratorCannotGrantMoreThanTheyHold()`; **Decision:** [ADR 0022](../../../adr/0022-administrators-cannot-grant-beyond-their-own-roles.md). |
| **Deny by Default** | Implemented | Every request not explicitly permitted requires authentication (`.requestMatchers("/**").authenticated()`), and every administration path additionally requires its specific authority; nothing is reachable by an unauthenticated or under-privileged request unless a rule explicitly allows it.<br><br>**Application code:** `WebSecurityConfiguration.securityFilterChain()`. |
| **Validate the Permissions on Every Request** | Implemented | Authorization is not decided once and cached: `@PreAuthorize`/`authorizeHttpRequests` re-evaluate on every request, and the user's `ROLE_` authorities themselves are reloaded from the database on every request rather than trusted from login, so a role or group change also takes effect immediately. Confirm against the deployed service that a group's role set change or a user's own group membership or enabled-status change takes effect on that user's very next request, without requiring re-login.<br><br>**Application code:** `LocalAuthorityRefreshFilter`; **Test code:** `LocalAuthorityRefreshFilterTest`; **Framework default:** Spring Security method security and `authorizeHttpRequests`; **Decision:** [ADR 0015](../../../adr/0015-per-request-local-authority-refresh.md). |
| **Thoroughly Review the Authorization Logic of Chosen Tools and Technologies, Implementing Custom Logic if Necessary** | Partial | **Misconfiguration:** the template does not use Spring Security's `hasRole()` uncritically: the stored `ROLE_MANAGE` role name already begins with `ROLE_`, so `hasRole()` cannot express the check. The Java configuration form rejects an argument starting with `ROLE_`, and the `@PreAuthorize` expression form strips one leading `ROLE_` and so checks `ROLE_MANAGE` rather than `ROLE_ROLE_MANAGE` (see [Authority resolution](#authority-resolution)). `RoleAdminController` and `WebSecurityConfiguration` instead use `hasAuthority("ROLE_ROLE_MANAGE")` for that one case, with the reasoning recorded in code.<br><br>**Vulnerable components:** not implemented. The build (`pom.xml`) and CI workflow (`build-and-test.yml`) run no dependency vulnerability scan such as OWASP Dependency-Check, and the repository defines no process for detecting and responding to vulnerable components.<br><br>**Application code:** `RoleAdminController` (comment above its `@PreAuthorize`), `WebSecurityConfiguration.securityFilterChain()`. |
| **Prefer Attribute and Relationship Based Access Control over RBAC** | Alternative | The template uses a role-based model (user → group → role → Spring Security authority; see [Building Block View](../../05-building-block-view.md#domain-model)) instead of ABAC or ReBAC, as a deliberate simplicity choice for a single-tenant administration model: direct user-to-role grants are intentionally unsupported to keep permissions understandable through group membership. Compared with the recommendation, it gives up object-level, relationship-, and attribute-based decisions; nothing else in the template supplies them. An adopter needing that granularity must extend or replace this model.<br><br>**Application code:** `AppUser`/`AppGroup`/`AppRole` and `LocalAuthoritiesOidcUserService`; **Decision:** [ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md). |
| **Ensure Lookup IDs are Not Accessible Even When Guessed or Cannot Be Tampered With** | Implemented | Every entity ID is a randomly generated UUID (`AbstractAuditableEntity`), not a sequential or otherwise guessable value, and every per-object lookup (`AdministrationService.user()`/`group()`/`role()`) is reached only after the path-level role check already gated the whole resource family. There is no per-object ownership check beyond that role gate, because the template has no concept of one user "owning" a subset of another management role's resources; see the least-privilege row above for the multi-tenant caveat.<br><br>**Application code:** `AbstractAuditableEntity` (UUID generation), `AdministrationService`. |
| **Enforce Authorization Checks on Static Resources** | Not applicable | The application serves no static resources or files of any kind; there is no `WebMvcConfigurer` resource handler, no `app-web-api-server/src/main/resources/static` or `public` directory, and no bundled frontend. Reassess if static content is ever added. |
| **Verify that Authorization Checks are Performed in the Right Location** | Implemented | All authorization is enforced by the Spring Security filter chain and `@PreAuthorize` method security running on the server; the application has no client-side code that could perform or be relied on for an authorization decision.<br><br>**Application code:** `WebSecurityConfiguration.securityFilterChain()`, `@PreAuthorize` on `UserAdminController`, `GroupAdminController`, `RoleAdminController`; **Framework default:** Spring Security `authorizeHttpRequests` and method security. |
| **Exit Safely when Authorization Checks Fail** | Implemented | Access-denied and CSRF failures are handled by one centralized handler that returns a generic RFC 9457 Problem Details response with no internal detail; a missing or conflicting resource is likewise handled centrally rather than by ad hoc per-endpoint logic (see [Error responses](error-responses.md)). A `@PreAuthorize` denial raised inside a controller reaches the same handler: `ApiResponseEntityExceptionHandler` rethrows `AccessDeniedException` to Spring Security instead of answering it as a 500 through its catch-all handler, and the denial is logged as `authorize_access`. Confirm access-denied and CSRF-failure responses never disclose which specific check failed or any internal detail.<br><br>**Application code:** `ProblemDetailAccessDeniedHandler`, `ApiResponseEntityExceptionHandler.rethrowAccessDenied()`; **Test code:** `ProblemDetailAccessDeniedHandlerTest`, `AdminControllerTest.userListRequiresUserManagementRole()`, `MethodSecurityAccessDeniedIntegrationTest.methodSecurityDenialReturnsAccessDeniedProblemDetailAndIsAudited()`. |
| **Implement Appropriate Logging** | Implemented | Authorization denials are recorded as structured ECS audit events (`event.category=[web, api]`, `event.type=[access, denied]`, `event.action=authorize_access`, `event.outcome=failure`), the same mechanism used for authentication and CSRF audit events (see [Logging](../06-logging-and-monitoring/README.md)). Confirm these events are recorded without credentials or tokens.<br><br>**Application code:** `SecurityAuditEventLogger.onAuthorizationDenied()`; **Test code:** `SecurityAuditEventLoggerTest.logsAuthorizationDenialForAnAnonymousUser()`. |
| **Create Unit and Integration Test Cases for Authorization Logic** | Implemented | `AdminApiIntegrationTest.eachApiAdmitsOnlyItsOwnManagementRole()` checks every administration API against every management role: each of `/admin/users`, `/admin/groups`, and `/admin/roles` admits only its own role, including the `hasAuthority("ROLE_ROLE_MANAGE")` special case, and `writesAreRestrictedToTheManagementRoleToo()` shows a write needs the same role. `AnonymousAccessIntegrationTest` shows every controller path rejects an unauthenticated request unless it is explicitly allowed, and that an unmapped path is denied rather than routed. `LocalAuthorityRefreshFilterTest` unit-tests the per-request reload of `ROLE_` authorities and the deauthentication of a disabled or deleted user.<br><br>**Test code:** `AdminApiIntegrationTest`, `AdminControllerTest.userListRequiresUserManagementRole()`, `AnonymousAccessIntegrationTest`, `LocalAuthorityRefreshFilterTest`. |
<!-- /ocsv:generated -->

Related documentation: [Building Block View](../../05-building-block-view.md#contained-building-blocks),
[Authentication](authentication.md),
[Sessions](sessions.md), [Logging](../06-logging-and-monitoring/README.md), and
[ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md).
