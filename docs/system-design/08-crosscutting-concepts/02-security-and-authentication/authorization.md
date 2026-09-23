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
- A user's own group membership or account status change revokes their session
  immediately; a group's role set changing, or a role being deleted, takes effect
  for its members only when they next authenticate.

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
groups, roles, and their relationships.

## Security behaviour

`LocalAuthorityRefreshFilter` reloads a user's `ROLE_` authorities from the
local user, group, and role model on every request, rather than trusting the
authorities computed once at login. Any authorization-relevant change,
including redefining a group's role set or deleting a role, takes effect on
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

| Status | Meaning |
| --- | --- |
| Implemented | The application's own code and configuration fulfill the recommendation. |
| Partial | The recommendation is only partly satisfied; the remainder is explained in the same row. |
| Deviates from the recommendation, by design | The template makes a deliberate, documented trade-off against the OWASP recommendation rather than an oversight. |
| Not applicable | The template has no capability or use case the recommendation addresses. |

This section covers each of the Cheat Sheet's own "Recommendations"
subsections, in its own order. "Introduction" and the "References" subsection
are skipped as non-normative: the former is motivational, the latter a
bibliography, neither has a testable claim.

| Recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Enforce Least Privileges | Partial | **Vertically (role-based):** each administration path requires the specific management authority for that resource family, not a general "admin" grant: `/admin/users/**` requires `ROLE_USER_MANAGE`, `/admin/groups/**` requires `ROLE_GROUP_MANAGE`, `/admin/roles/**` requires `ROLE_ROLE_MANAGE`.<br><br>**Horizontally (per-owner/per-tenant):** not applicable today. There is no per-owner or per-tenant resource boundary to separate, because the template has no multi-tenancy; any holder of a management role administers the entire corresponding collection by design. An adopter that introduces multi-tenancy or per-user resource ownership must add its own horizontal checks.<br><br>**Application configuration:** `WebSecurityConfiguration.securityFilterChain()` (`.hasRole("USER_MANAGE")`, `.hasRole("GROUP_MANAGE")`, `.hasAuthority("ROLE_ROLE_MANAGE")`); **application code:** `@PreAuthorize` on `UserAdminController`, `GroupAdminController`, `RoleAdminController`. |
| Deny by Default | Implemented | Every request not explicitly permitted requires authentication (`.requestMatchers("/**").authenticated()`), and every administration path additionally requires its specific authority; nothing is reachable by an unauthenticated or under-privileged request unless a rule explicitly allows it.<br><br>**Application configuration:** `WebSecurityConfiguration.securityFilterChain()`. |
| Validate the Permissions on Every Request | Implemented | Authorization is not decided once and cached: `@PreAuthorize`/`authorizeHttpRequests` re-evaluate on every request, and the user's `ROLE_` authorities themselves are reloaded from the database on every request rather than trusted from login, so a role or group change also takes effect immediately. Confirm against the deployed service that a group's role set change, a role deletion, or a user's own group membership or enabled-status change takes effect on that user's very next request, without requiring re-login.<br><br>**Application code:** `LocalAuthorityRefreshFilter`; **Spring Security:** method security and `authorizeHttpRequests`. See [ADR 0015](../../../adr/0015-per-request-local-authority-refresh.md). |
| Thoroughly Review the Authorization Logic of Chosen Tools and Technologies, Implementing Custom Logic if Necessary | Implemented | The template does not use Spring Security's `hasRole()` uncritically: the stored `ROLE_MANAGE` role name already begins with `ROLE_`, so `hasRole()` cannot express the check. The Java configuration form rejects an argument starting with `ROLE_`, and the `@PreAuthorize` expression form strips one leading `ROLE_` and so checks `ROLE_MANAGE` rather than `ROLE_ROLE_MANAGE` (see [Authority resolution](#authority-resolution)). `RoleAdminController` and `WebSecurityConfiguration` instead use `hasAuthority("ROLE_ROLE_MANAGE")` for that one case, with the reasoning recorded in code.<br><br>**Application code:** `RoleAdminController` (comment above its `@PreAuthorize`), `WebSecurityConfiguration.securityFilterChain()`. |
| Prefer Attribute and Relationship Based Access Control over RBAC | Deviates from the recommendation, by design | The template uses a role-based model (user → group → role → Spring Security authority; see [Building Block View](../../05-building-block-view.md#domain-model)) rather than ABAC or ReBAC. This is a deliberate simplicity trade-off for a single-tenant administration model: direct user-to-role grants are intentionally unsupported to keep permissions understandable through group membership. An adopter needing fine-grained, relationship-, or attribute-based access control must extend or replace this model.<br><br>**Application code:** `AppUser`/`AppGroup`/`AppRole` and `LocalAuthoritiesOidcUserService`. |
| Ensure Lookup IDs are Not Accessible Even When Guessed or Cannot Be Tampered With | Implemented | Every entity ID is a randomly generated UUID (`AbstractAuditableEntity`), not a sequential or otherwise guessable value, and every per-object lookup (`AdministrationService.user()`/`group()`/`role()`) is reached only after the path-level role check already gated the whole resource family. There is no per-object ownership check beyond that role gate, because the template has no concept of one user "owning" a subset of another management role's resources; see the least-privilege row above for the multi-tenant caveat.<br><br>**Application code:** `AbstractAuditableEntity` (UUID generation), `AdministrationService`. |
| Enforce Authorization Checks on Static Resources | Not applicable | The application serves no static resources or files of any kind; there is no `WebMvcConfigurer` resource handler and no bundled frontend. Reassess if static content is ever added. |
| Verify that Authorization Checks are Performed in the Right Location | Implemented | All authorization is enforced by the Spring Security filter chain and `@PreAuthorize` method security running on the server; the application has no client-side code that could perform or be relied on for an authorization decision.<br><br>**Spring Security:** `authorizeHttpRequests`, method security. |
| Exit Safely when Authorization Checks Fail | Implemented | Access-denied and CSRF failures are handled by one centralized handler that returns a generic RFC 9457 Problem Details response with no internal detail; a missing or conflicting resource is likewise handled centrally rather than by ad hoc per-endpoint logic. Confirm access-denied and CSRF-failure responses never disclose which specific check failed or any internal detail.<br><br>**Application code:** `ProblemDetailAccessDeniedHandler`, `ApiResponseEntityExceptionHandler`. See [Error responses](error-responses.md). |
| Implement Appropriate Logging | Implemented | Authorization denials are recorded as structured ECS audit events (`event.category=[web, api]`, `event.type=[access, denied]`, `event.action=authorize_access`, `event.outcome=failure`), the same mechanism used for authentication and CSRF audit events. Confirm these events are recorded without credentials or tokens.<br><br>**Application code:** `SecurityAuditEventLogger.onAuthorizationDenied()`. See [Logging](../06-logging-and-monitoring/README.md). |
| Create Unit and Integration Test Cases for Authorization Logic | Partial | `AdminControllerTest.userListRequiresUserManagementRole()` asserts that `GET /admin/users` returns 403 for a caller without `ROLE_USER_MANAGE` and succeeds for one with it. No test covers the authorization of the other user administration operations or of the group (`/admin/groups/**`) and role (`/admin/roles/**`) management paths, including the `hasAuthority("ROLE_ROLE_MANAGE")` special case.<br><br>**Test code:** `AdminControllerTest.userListRequiresUserManagementRole()`. |

Related documentation: [Building Block View](../../05-building-block-view.md#contained-building-blocks),
[Authentication](authentication.md),
[Sessions](sessions.md), [Logging](../06-logging-and-monitoring/README.md), and
[ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md).
