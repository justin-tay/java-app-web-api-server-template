<!-- arc42-generated -->
# Architecture patterns

Structural patterns that recur across the codebase rather than belonging to
one class. Filter-chain composition is documented in depth in
[Logging](../06-logging-and-monitoring/README.md#design-and-ownership) already, since almost
every filter's reason for existing is the log/audit event it produces; this
page covers the patterns that sit above and around it.

## Layering

Every resource follows the same three layers, with no layer skipped and no
alternative pattern elsewhere in the codebase:

`@RestController` (`api/admin/*AdminController`) -> `@Service @Transactional`
(`AdministrationService`) -> `JpaRepository`/`JpaSpecificationExecutor`
(`domain/*Repository`).

* Controllers depend only on the service, never on a repository directly.
  A controller's job is HTTP binding, validation triggering (`@Valid`),
  authorization (`@PreAuthorize`), and mapping between DTOs and domain
  entities (see [Domain concepts](../01-domain-model/README.md)); it holds no
  business rules.
* `AdministrationService` is the single `@Transactional` service for the
  whole admin domain, covering users, groups, and roles together rather than
  one service per entity. It owns invariants a repository cannot express
  alone, such as rejecting a duplicate group name or refusing to delete a
  role still assigned to a group, by throwing `ConflictException` or
  `ResourceNotFoundException` before the repository is touched.
* Repositories extend both `JpaRepository` and `JpaSpecificationExecutor`.
  Simple lookups are declared as derived query methods
  (`existsByName`, `existsByGroups_Id`); multi-criteria, optional-filter
  list queries (`AdministrationService.users(...)`, `.groups(...)`,
  `.roles(...)`) are instead composed at the service layer from small private
  `Specification<T>` factory methods (`contains`, `equals`, `distinct`)
  rather than generating a query method per filter combination or
  introducing a separate query-object class per entity.

## Exception-to-response mapping

The template distinguishes exceptions that name a domain outcome from the
generic exception handling that turns any exception into a response body.
Domain-specific exceptions are deliberately minimal and unchecked:
`ResourceNotFoundException` (admin resource missing), `ConflictException`
(name collision, or a delete blocked by a still-referencing row), and the
API-level `BadRequestException`. None of them carry HTTP status or Problem
Details concerns themselves; `ApiResponseEntityExceptionHandler` is the only
place that maps an exception type to an HTTP status, a `urn:problem:*` type,
and a redacted audit event. The full type-by-type mapping, including the
firewall- and Tomcat-level responses that never reach this handler, is
maintained once, in
[Error responses](../02-security-and-authentication/error-responses.md), rather than repeated here.

## Query composition

List endpoints accept independent optional filters (name, role, group,
enabled) that combine with logical AND only when supplied. Rather than
branching over every filter combination, `AdministrationService` builds a
`Specification<T>` per non-null filter and combines them with
`Specification.allOf(...)`, wrapping the result in a `distinct(...)` helper
when a filter joins across a `ManyToMany` association (`groups`, `roles`) to
avoid duplicate rows from the join. This is the only dynamic-query pattern in
the codebase; there is no separate criteria-builder or QueryDSL layer.

## Authorization checks at the layer boundary

Method-level authorization (`@PreAuthorize`) is declared on the controller,
one role per admin resource (`GROUP_MANAGE` on `GroupAdminController`,
`USER_MANAGE` on `UserAdminController`, `ROLE_MANAGE` on
`RoleAdminController`, the last expressed as `hasAuthority('ROLE_ROLE_MANAGE')`
rather than `hasRole('ROLE_MANAGE')` since the two are equivalent under
Spring Security's `ROLE_` prefix convention), not on the service. The
service trusts that a caller reaching it is already authorized; it enforces
only domain invariants, not access control. This keeps the two concerns
(who may call this endpoint, and whether this operation is domain-valid)
independently testable and independently visible at the point that matters
for each: the HTTP boundary for authorization, the service for business
rules.

<!-- /arc42-generated -->
