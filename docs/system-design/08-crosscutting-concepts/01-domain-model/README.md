<!-- arc42-generated -->
# Domain Model

The template's only business domain is local user administration: users,
roles, and permissions used for authorization, layered on top of Keycloak-issued
authentication. Everything under `com.example.commons.accounts.domain` and
`com.example.commons.accounts.admin` exists to let an operator manage that
model through a REST API; there is no further product domain to model until
an adopter builds one.

## Entity model

| Entity | Table | Purpose |
| --- | --- | --- |
| `AppUser` | `app_user` | A local account. Its `publicId` is the permanent identity, a random UUID the application generates and never reuses, and `username` is the unique login handle, treated as immutable. `name` is the person's name as one string, so no structure such as a given and family name is assumed; it is the OIDC `name` claim and the ECS `user.full_name` log field, whereas ECS `user.name` is the username. It also holds an email, an optional free-text `department` that the account review shows and filters by, a status (`ACTIVE` or `SUSPENDED`, with when and why it was suspended), the inactivity clock (`inactivityClockStartedAt`, the creation or last unsuspension time), the time of the last sign-in (`lastLoginAt`, null until the first, set only by a sign-in, see [ADR 0028](../../../adr/0028-user-last-login-and-dormant-account-disabling.md) and [ADR 0031](../../../adr/0031-inactive-account-suspension-and-removal.md)), and a `ManyToMany` set of `AppRole`s it holds. Removing an account deletes the row. Whether it is privileged, because a role it holds has a privileged permission, is computed and not stored. |
| `AppRole` | `app_role` | A named set of permissions that a user can hold, the sole way a user receives permissions. The name is only a label, so it can change; the permissions are held through the `app_role_permission` join table. |
| `AppPermission` | `app_permission` | Something a user may do, as a `domain` and an `action` whose authority is `domain:action`, with a `privileged` flag. It is reference data the schema seeds, because the code checks it by name, and the API only reads it. The pairs of permissions that no user may hold together are in `app_permission_conflict` ([ADR 0038](../../../adr/0038-role-permission-model-and-account-review-classes.md)). |
| `AccountAuditEvent` | `account_audit_event` | One append-only row of the business audit trail: when, who, what, the target, the reason, and the changed values as JSON. It has no foreign key to the account, so it outlives it ([ADR 0030](../../../adr/0030-business-audit-trail-table.md)). |
| `AppSetting` | `app_setting` | One application setting: the inactivity thresholds, whether the review runs, and the privileged and non-privileged review intervals in months (1, 3, 6 or 12). |
| `Task` | `task` | Something to do by a date, such as the account review; the dashboard is written against it ([ADR 0032](../../../adr/0032-periodic-account-review.md), [ADR 0037](../../../adr/0037-account-review-populations-and-stored-report.md)). A review task is a privileged or a non-privileged account review and runs for one calendar month in each of its review months. |
| `AccountReviewItem` | `account_review_item` | One active account's entry in a review task. Its outcome is pending, confirmed, confirmed with roles edited, or removed. While pending it shows the live account; when decided, or when the account is removed outside the review, the department, last login and the role names before and after are frozen on it, and a privileged review's item also holds the privileged permissions the account had. It has no foreign key to the account and points at the removal's audit event instead of copying the reason. |
| `AccountReviewAttestation` | `account_review_attestation` | A reviewer's confirmation of the suspended or the removed population of a privileged review task, made once. |
| `AccountReviewPopulationEntry` | `account_review_population_entry` | One account of a confirmed population as it was when confirmed. |
| `AccountReviewReport` | `account_review_report` | The PDF stored when a task completes, with its SHA-256, written once and never regenerated. |
| `AbstractAuditableEntity` | (mapped superclass) | Supplies `createdAt`/`updatedAt` timestamps to the entities above, and the two identifiers of `AbstractIdentifiedEntity`, via `@PrePersist`/`@PreUpdate` callbacks. `touch()` lets a caller stamp `updatedAt` immediately after mutating a collection association that JPA's own dirty-checking would not otherwise flag as a field change. |

Identifiers are application-generated random UUIDs (`String` primary keys),
not database-generated sequences; this keeps ID generation independent of the
database platform and avoids exposing a monotonic count. Permission assignment is
always `AppUser -> AppRole -> AppPermission`, and `LocalAuthorityRefreshFilter`
(see [Architecture patterns](../03-architecture-patterns/README.md)) walks this same path
on every request to resolve a user's authorities.

## API model and DTO conventions

Request and response shapes live in one file per resource area,
[`AdminDtos`](../../../../commons-accounts/src/main/java/com/example/commons/accounts/admin/AdminDtos.java),
as a namespace of `record` types rather than individual top-level classes.
The conventions this template follows:

* **Request/response separation.** `UserCreateRequest`, `UserUpdateRequest`,
  `GroupRequest`, and `RoleRequest` are distinct from `UserResponse`,
  `GroupResponse`, and `RoleResponse`; no entity or request type is ever
  serialized directly as a response, and no response type is ever bound
  directly from a request body.
* **Bean Validation on the request record's components.** Constraints
  (`@Username`, `@ResourceName`, `@Email`, `@NotEmpty`) are declared once, on
  the request record itself, and enforced by `@Valid @RequestBody` in the
  controller; see [Error responses](../02-security-and-authentication/error-responses.md) for how a
  rejected constraint becomes a `urn:problem:validation-failed` response.
* **`Summary` for nested references.** A related entity referenced from
  another resource's response (a role's permissions, a user's roles) is
  represented by the minimal `Summary(id, name)` record rather than the
  related resource's full response type, so a `UserResponse` does not
  recursively pull in the same shape as `GET /admin/roles/{id}`.
* **`PageResponse<T>` as the one pagination envelope.** Every list endpoint
  returns `PageResponse<T>(items, page, size, totalItems, totalPages)`,
  built from Spring Data's `Page<T>` in the controller; there is no
  alternative pagination shape in the API.
* **Manual entity/DTO mapping in the controller.** Controllers map an entity
  to its response record with a small private `response(...)` method (for
  example `GroupAdminController.response`); the template does not use
  MapStruct or a generic mapping library, keeping the mapping visible next to
  the endpoint that needs it.

## Domain language

Shared vocabulary for terms this documentation uses consistently (such as
*deployment decision required* versus *deployment responsibility*, and
*control implementation*) is recorded once in [`CONTEXT.md`](../../../../CONTEXT.md)
at the repository root; consult it before introducing a new term for a
concept it already names.

Three composed Bean Validation constraints encode the template's own
naming rules and are reused across both the entity layer and the DTO layer
so a name is validated identically wherever it is accepted:

| Constraint | Applied to | Rule |
| --- | --- | --- |
| `@Username` | `AppUser.username`, `UserCreateRequest.username` | Not blank, at most 100 characters. |
| `@ResourceName` | `AppUser.name`, `AppRole.name`, `UserCreateRequest`/`UserUpdateRequest.name`, `RoleRequest.name` | Not blank, at most 100 characters. |

The two are structurally identical today; they are kept as distinct
annotations, each with its own message key
(`{validation.username}`,
`{validation.resource-name}` in
[`ValidationMessages.properties`](../../../../commons-accounts/src/main/resources/ValidationMessages.properties)),
so a username rule and a resource name rule can diverge later without a
call-site change.

<!-- arc42-manual: Once the template is adopted for a real business domain, replace this page's scope with that domain's own entities, DTOs, and ubiquitous language, and keep the user/group/role model only as the authorization foundation it remains beneath the new domain. -->
<!-- /arc42-generated -->
