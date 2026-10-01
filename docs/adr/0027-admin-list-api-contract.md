# ADR 0027: Admin list API contract

## Status

Accepted

## Context

The admin list endpoints (`/admin/users`, `/admin/groups`, `/admin/roles`)
support `page`, `size`, a single `sort` property and a few per-field filters,
returned in a `PageResponse` envelope. A data table in a single-page frontend
needs more: one search box across several fields, sorting by more than one
column, filters for status and date ranges, and type-ahead pickers for groups
and roles.

Standards were considered against that need:

* JSON:API (`page[...]`, `filter[...]`, `sort=-field`) would replace the
  existing envelope and parameter shapes without adding a capability the
  frontend needs.
* RSQL/FIQL, OData `$filter` and AIP-160 `filter` are filter languages. Each
  needs a parser and widens the input surface for injection and expensive
  queries, to serve three small entities whose filterable fields are known.
* Cursor pagination (AIP-158) cannot provide a total count or jump to a
  page, and admin lists are small enough that offset pagination is cheap.
* GraphQL connections would add a second API style to the template.

## Decision

Keep and extend the current convention. Filters stay fixed, per-field,
whitelisted parameters combined with AND, evaluated through JPA
specifications. No filter language is introduced.

* **Pagination:** unchanged. `page` (default 0), `size` (default 20, 1 to
  100) and the `PageResponse(items, page, size, totalItems, totalPages)`
  envelope, which keeps the total count a table needs. The 100 cap stays.
* **Sorting:** `sort` may repeat, as `sort=username,asc&sort=createdAt,desc`,
  matching Spring Data. Each property must be in the endpoint's whitelist, at
  most 3 sorts are accepted, and a repeated property is rejected. A single
  `sort` behaves as it does today.
* **Search:** a `search` parameter (max 100 characters) is a case-insensitive
  contains match, ORed across the endpoint's search fields and ANDed with
  the other filters. Users search `username`, `name` and `email`, plus an
  exact match on `id`. Groups search `name`, and roles search `name` and `displayName`. LIKE wildcards in the
  input are escaped.
* **Filters added:** users gain `email` (contains), `status` (`active` or
  `suspended`) and `neverSignedIn` (see ADR 0033), `createdFrom` and `createdTo`
  (inclusive ISO 8601 dates, UTC). Existing filters are unchanged.
* **Pickers:** group and role pickers use `search` with normal paging (type-ahead,
  next page on scroll). No unpaged or larger-cap endpoint is added.
* **Rename:** the user field `displayName` becomes `name` in the entity,
  database column, DTOs, filter, sort whitelist and claim mapping, in the same
  change, so the contract breaks once.
* **Errors:** unchanged, RFC 9457 Problem Details (ADR 0013). An unknown
  sort property, direction, status value or malformed date is a 400.
* **Documentation:** the contract is described in prose in the system design
  docs. Publishing an OpenAPI specification is a separate decision.

## Consequences

The frontend data table can drive search, multi-column sort, filters and
paging from its state with no client-side filtering. Adopters add a filter by
adding a whitelisted parameter and a specification, and cannot be handed an
unbounded or arbitrary query.

Contains matches on `search` cannot use an ordinary B-tree index, which is
acceptable at admin-list sizes and should be revisited if user counts grow
large. The `displayName` rename is a breaking change for any existing client
of these endpoints, including the companion frontend.
