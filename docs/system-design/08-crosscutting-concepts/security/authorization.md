# Authorization

Authorization converts an authenticated OpenID Connect (OIDC) identity into
local application permissions. Authentication is the responsibility of the
external identity provider; application authorisation is owned and enforced
by this application. The user/group/role data model this relies on, and the
components involved, are described in
[Building Block View](../../05-building-block-view.md#components-authentication-and-authorization);
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

`ROLE_MANAGE` is checked with `hasAuthority("ROLE_ROLE_MANAGE")`. It must not
be checked with `hasRole`, because `hasRole` adds the `ROLE_` prefix and the
stored role name already begins with `ROLE`.

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
| Implemented (vertical); not applicable (horizontal) | The recommendation is satisfied for vertical (role-based) access control; there is no horizontal (per-owner/per-tenant) dimension for it to apply to, because the template is single-tenant. |
| Deviates from the recommendation, by design | The template makes a deliberate, documented trade-off against the OWASP recommendation rather than an oversight. |
| Not applicable to this template | The template has no capability or use case the recommendation addresses. |

| OWASP recommendation | Status | Implementation Statement |
| --- | --- | --- |
| Enforce least privilege | Implemented (vertical); not applicable (horizontal) | Each administration path requires the specific management authority for that resource family, not a general "admin" grant: `/admin/users/**` requires `ROLE_USER_MANAGE`, `/admin/groups/**` requires `ROLE_GROUP_MANAGE`, `/admin/roles/**` requires `ROLE_ROLE_MANAGE`. There is no per-owner or per-tenant resource boundary to separate horizontally, because the template has no multi-tenancy; any holder of a management role administers the entire corresponding collection by design. An adopter that introduces multi-tenancy or per-user resource ownership must add its own horizontal checks. **Application configuration:** `WebSecurityConfiguration.securityFilterChain()` (`.hasRole("USER_MANAGE")`, `.hasRole("GROUP_MANAGE")`, `.hasAuthority("ROLE_ROLE_MANAGE")`); **application code:** `@PreAuthorize` on `UserAdminController`, `GroupAdminController`, `RoleAdminController`. |
| Deny by default | Implemented | Every request not explicitly permitted requires authentication (`.requestMatchers("/**").authenticated()`), and every administration path additionally requires its specific authority; nothing is reachable by an unauthenticated or under-privileged request unless a rule explicitly allows it. **Application configuration:** `WebSecurityConfiguration.securityFilterChain()`. |
| Validate permissions on every request | Implemented | Authorization is not decided once and cached: `@PreAuthorize`/`authorizeHttpRequests` re-evaluate on every request, and the user's `ROLE_` authorities themselves are reloaded from the database on every request rather than trusted from login, so a role or group change also takes effect immediately. Confirm against the deployed service that a group's role set change, a role deletion, or a user's own group membership or enabled-status change takes effect on that user's very next request, without requiring re-login. **Application code:** `LocalAuthorityRefreshFilter`; **Spring Security:** method security and `authorizeHttpRequests`. See [ADR 0015](../../../adr/0015-per-request-local-authority-refresh.md). |
| Thoroughly review the authorization logic of chosen tools, implementing custom logic where needed | Implemented | The template does not use Spring Security's `hasRole()` uncritically: the stored `ROLE_MANAGE` role name already begins with `ROLE`, so `hasRole()`'s automatic `ROLE_` prefixing would double it. `RoleAdminController` and `WebSecurityConfiguration` instead use `hasAuthority("ROLE_ROLE_MANAGE")` for that one case, with the reasoning recorded in code. **Application code:** `RoleAdminController` (comment above its `@PreAuthorize`), `WebSecurityConfiguration.securityFilterChain()`. |
| Prefer attribute- or relationship-based access control over role-based access control | Deviates from the recommendation, by design | The template uses a role-based model (user → group → role → Spring Security authority; see [Building Block View](../../05-building-block-view.md#authorization-model)) rather than ABAC or ReBAC. This is a deliberate simplicity trade-off for a single-tenant administration model: direct user-to-role grants are intentionally unsupported to keep permissions understandable through group membership. An adopter needing fine-grained, relationship-, or attribute-based access control must extend or replace this model. **Application code:** `AppUser`/`AppGroup`/`AppRole` and `LocalAuthoritiesOidcUserService`. |
| Ensure lookup IDs cannot be guessed or tampered with, and enforce access control on every specific object request | Implemented | Every entity ID is a randomly generated UUID (`AbstractAuditableEntity`), not a sequential or otherwise guessable value, and every per-object lookup (`AdministrationService.user()`/`group()`/`role()`) is reached only after the path-level role check already gated the whole resource family. There is no per-object ownership check beyond that role gate, because the template has no concept of one user "owning" a subset of another management role's resources; see the least-privilege row above for the multi-tenant caveat. **Application code:** `AbstractAuditableEntity` (UUID generation), `AdministrationService`. |
| Enforce authorization checks on static resources | Not applicable to this template | The application serves no static resources or files of any kind; there is no `WebMvcConfigurer` resource handler and no bundled frontend. Reassess if static content is ever added. |
| Verify that authorization checks are performed server-side | Implemented | All authorization is enforced by the Spring Security filter chain and `@PreAuthorize` method security running on the server; the application has no client-side code that could perform or be relied on for an authorization decision. **Spring Security:** `authorizeHttpRequests`, method security. |
| Exit safely when authorization checks fail | Implemented | Access-denied and CSRF failures are handled by one centralized handler that returns a generic RFC 9457 Problem Details response with no internal detail; a missing or conflicting resource is likewise handled centrally rather than by ad hoc per-endpoint logic. Confirm access-denied and CSRF-failure responses never disclose which specific check failed or any internal detail. **Application code:** `ProblemDetailAccessDeniedHandler`, `ApiResponseEntityExceptionHandler`. See [Error responses](error-responses.md). |
| Implement appropriate logging | Implemented | Authorization denials are recorded as structured ECS audit events (`event.category=[web, api]`, `event.type=[access, denied]`, `event.action=authorize_access`, `event.outcome=failure`), the same mechanism used for authentication and CSRF audit events. Confirm these events are recorded without credentials or tokens. **Application code:** `SecurityAuditEventLogger.onAuthorizationDenied()`. See [Logging](../logging/README.md). |
| Create unit and integration test cases for authorization logic | Implemented | `AdminControllerTest` asserts that each administration endpoint returns 403 for a caller without the required authority and succeeds for one with it, across the user, group, and role management paths. **Test code:** `AdminControllerTest`. |

Related documentation: [Building Block View](../../05-building-block-view.md#components-authentication-and-authorization),
[Authentication](authentication.md),
[Sessions](sessions.md), [Logging](../logging/README.md), and
[ADR 0005](../../../adr/0005-keycloak-authentication-local-authorisation.md).
