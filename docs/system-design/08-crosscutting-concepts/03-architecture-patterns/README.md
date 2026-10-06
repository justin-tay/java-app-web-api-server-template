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
  whole admin domain, covering users and roles together rather than
  one service per entity. It owns invariants a repository cannot express
  alone, such as rejecting a duplicate role name or refusing to delete a
  role still held by a user, by throwing `ConflictException` or
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

List endpoints accept independent optional filters (search, name, role, group,
status, created date range) that combine with logical AND only when supplied. Rather than
branching over every filter combination, `AdministrationService` builds a
`Specification<T>` per non-null filter and combines them with
`Specification.allOf(...)`, wrapping the result in a `distinct(...)` helper
when a filter joins across a `ManyToMany` association (`roles`, `permissions`) to
avoid duplicate rows from the join. This is the only dynamic-query pattern in
the codebase; there is no separate criteria-builder or QueryDSL layer.

## Authorization checks at the layer boundary

Method-level authorization (`@PreAuthorize`) is declared on the controller,
one permission per endpoint (`user:create` on creating a user,
`role:add-permission` on giving a role a permission, expressed as
`hasAuthority('user:create')`, since an authority is the permission's name with no
prefix), not on the service. The service trusts that a caller reaching it holds the
permission of the endpoint; it enforces domain invariants, and the rules that
depend on what a request asks for, which an endpoint-level check cannot see: which
of `user:add-role`, `user:remove-role` and `user:update` a user update needs, that
a privileged permission is granted only by someone who holds it, and that
conflicting permissions are kept apart. This keeps the two concerns
(who may call this endpoint, and whether this operation is domain-valid)
independently testable and independently visible at the point that matters
for each: the HTTP boundary for authorization, the service for business
rules.

<!-- /arc42-generated -->
