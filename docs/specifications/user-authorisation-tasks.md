# Implementation Plan: Local User Authorisation

## Sequence

Foundation and schema are completed first, followed by domain validation,
administration APIs, login integration, and end-to-end verification. Each task is
independently testable and is ordered by its implementation dependencies.

- [ ] 1. Add persistence and migration foundation

- [ ] 1.1 Add JPA, Liquibase, and datasource configuration
  - Add `spring-boot-starter-data-jpa` and `liquibase-core` dependencies in
    `pom.xml`; configure the production JDBC driver selected by deployment.
  - Set `spring.liquibase.change-log`, configure test/local H2 datasources, and
    disable Hibernate schema generation in `src/main/resources/application*.yaml`.
  - Confirm the existing Spring Session JDBC tables remain compatible with the
    selected schema-management arrangement.
  - Completion: application context starts against a blank test datasource and
    Hibernate does not generate the authorisation schema.
  - _Requirements: R6_

- [ ] 1.2 Create portable Liquibase formatted-SQL changesets
  - Add a master changelog and ordered formatted-SQL schema/seed changesets under
    `src/main/resources/db/changelog/`.
  - Create `app_user`, `app_group`, `app_role`, `app_user_group`, and
    `app_group_role`, including portable constraints and lookup indexes.
  - Use fixed UUID-string literals for all fixture IDs and application-compatible
    `CHAR(36)`, `VARCHAR`, `BOOLEAN`, and `TIMESTAMP` column definitions.
  - Completion: applying changesets twice is idempotent and no vendor-specific
    generated-ID, UUID, JSON, or upsert SQL is present.
  - _Requirements: R6_

- [ ] 1.3 Seed aligned Keycloak and application fixtures
  - Create `bin/seed-test-data.js` to provision immutable Keycloak usernames
    `admin`, `test-user`, and `multi-group-user`, each with the test-only password
    `password`; do not modify `bin/configure-keycloak.js`.
  - Do not assign Keycloak realm or client roles. Local application roles are the
    only source of application authorisation.
  - Seed `USER_MANAGE`, `GROUP_MANAGE`, `ROLE_MANAGE`, and `APPLICATION_USER`;
    the `Administrators` and `Test Users` groups; and matching enabled local users.
  - Associate the fixtures exactly as designed, including multi-group membership.
  - Completion: a local test setup has matching Keycloak and Liquibase identities.
  - _Requirements: R1, R3, R6_

- [ ] 2. Implement the domain model and shared Bean Validation

- [ ] 2.1 Create reusable validation constraints
  - Add composed Bean Validation annotations and shared limits for username,
    display name, and resource name in a validation package.
  - Define safe, stable validation message keys for API presentation.
  - Completion: unit tests validate valid, blank, overlong, malformed-email, and
    invalid-name values through the shared annotations.
  - _Requirements: R1, R2, R3, R5_

- [ ] 2.2 Implement JPA entities and association mappings
  - Create `AppUser`, `AppGroup`, and `AppRole` under `domain`, using
    application-generated UUID-string IDs, timestamps, entity-level constraints,
    and the two many-to-many associations.
  - Ensure users require non-empty groups, group/role collections reject duplicate
    links, and JPA entities are not used as controller request bodies.
  - Completion: JPA integration tests prove mapping correctness and entity Bean
    Validation rejects invalid persistent state.
  - _Requirements: R1, R2, R3, R6_

- [ ] 2.3 Implement repositories and authority lookup
  - Create Spring Data repositories and an entity-graph or fetch-join query that
    finds an enabled user by username with all group roles in one query.
  - Add repository filtering support for the approved user, group, and role
    filters, plus pageable/sortable lookup methods.
  - Completion: integration tests cover unique values, authority aggregation,
    filters, sorts, and no lazy-loading failure after lookup.
  - _Requirements: R1, R2, R3, R4, R5_

- [ ] 3. Implement administration services and API contract

- [ ] 3.1 Define request/response DTOs and pageable result model
  - Create separate create/update DTOs for users, groups, and roles; omit username
    from user update DTOs.
  - Apply shared Bean Validation to DTO fields, `@NotEmpty` to user `groupIds`,
    and create response DTOs that expose summaries rather than JPA entities.
  - Define the `items`, `page`, `size`, `totalItems`, `totalPages` list response.
  - Completion: DTO validation tests demonstrate field-specific failures and no
    API model serializes entity association internals.
  - _Requirements: R1, R2, R3, R5_

- [ ] 3.2 Build transactional user administration service
  - Implement create, get, list, update, and delete operations; preserve immutable
    username and validate non-empty group memberships.
  - Resolve group IDs, distinguish unknown resources from malformed values, map
    conflicts, and generate application timestamps/UUIDs.
  - Completion: service tests cover successful CRUD, missing groups, duplicate
    usernames, no-group attempts, lifecycle changes, and deletion.
  - _Requirements: R1, R5_

- [ ] 3.3 Build transactional group and role administration services
  - Implement group CRUD with replacement role assignment and deletion refusal for
    groups containing users.
  - Implement role CRUD with deletion refusal while assigned to a group.
  - Completion: service tests cover replacement semantics, empty role sets,
    duplicate names, unknown role IDs, and both deletion conflicts.
  - _Requirements: R2, R3, R5_

- [ ] 3.4 Expose administration controllers with offset pagination and filtering
  - Add `UserAdminController`, `GroupAdminController`, and `RoleAdminController`
    for the `/admin/users`, `/admin/groups`, and `/admin/roles` resources.
  - Use `@Valid` on request bodies, `@Validated` on controllers, parameter bounds
    for `page`/`size`, and whitelisted filter/sort fields.
  - Return `201` plus `Location`, `200`, `204`, and the prescribed page metadata.
  - Completion: MockMvc tests cover every CRUD method, defaults, page bounds,
    sorting, filters, and empty lists.
  - _Requirements: R1, R2, R3, R5_

- [ ] 4. Standardise API problem details

- [ ] 4.1 Add field and global validation-error responses
  - Extend `ApiResponseEntityExceptionHandler` to handle request-body validation,
    method/query validation, and malformed JSON before its generic handler.
  - Return an RFC 9457 `ProblemDetail` with an `errors` extension. Each attributable
    error uses `source.pointer` with a JSON Pointer; global errors omit the pointer.
  - Completion: tests prove `/displayName` and `/groupIds/0` mapping, global error
    handling, safe error output, and invalid pagination responses.
  - _Requirements: R5_

- [ ] 4.2 Map administration domain errors
  - Add explicit not-found and conflict exception types and problem-detail mappings
    ahead of the catch-all handler.
  - Preserve `404` for missing references/resources and `409` for duplicate or
    relationship-protected deletion requests without exposing persistence details.
  - Completion: MockMvc tests validate response status and safe body content for
    all defined error categories.
  - _Requirements: R1, R2, R3, R5_

- [ ] 5. Integrate local roles with OIDC login and Spring Security

- [ ] 5.1 Implement `LocalAuthoritiesOidcUserService`
  - Refactor the current inline `oidcUserService()` flow in
    `WebSecurityConfiguration` into an injectable service that delegates to
    `OidcUserService`; remove the existing Keycloak realm-role mapping.
  - Read `preferred_username`, require an enabled local user, aggregate distinct
    group roles as one `ROLE_<name>` authority each, and fail authentication for a
    missing/unknown/disabled user or lookup failure. Do not translate Keycloak
    realm or client roles into application authorities.
  - Completion: security tests cover matching users, group-role union,
    duplicate-role de-duplication, missing claim, unknown/disabled user, and
    persistence failure.
  - _Requirements: R1, R3, R4_

- [ ] 5.2 Enforce management authorities by endpoint family
  - Add ordered `/admin/users/**`, `/admin/groups/**`, and `/admin/roles/**`
    matchers before the existing broad authenticated matcher.
  - Require `hasRole("USER_MANAGE")`, `hasRole("GROUP_MANAGE")`, and
    `hasRole("ROLE_MANAGE")`, respectively.
  - Completion: MockMvc/Spring Security tests prove allowed and forbidden access
    independently for every management role.
  - _Requirements: R3, R5_

- [ ] 6. Verify migrations, security, and regression quality

- [ ] 6.1 Run an end-to-end integration suite
  - Start from an empty H2 test database, apply Liquibase, execute CRUD through
    MockMvc, and verify fixture-backed OIDC authority enrichment.
  - Confirm role/group changes take effect at the next login and no direct
    user-role grant can occur.
  - Completion: the required happy paths, validation paths, deletion guards, and
    login denial behavior are covered together.
  - _Requirements: R1, R2, R3, R4, R5, R6_

- [ ] 6.2 Run project quality gates and document operation
  - Run the Maven test suite and Spring Java Format; correct any regressions.
  - Update `README.md` with datasource/Liquibase setup, seeded credentials/users,
    management-role behavior, and Keycloak username immutability expectations.
  - Completion: tests and formatter pass and operators can reproduce local setup.
  - _Requirements: R1, R3, R4, R5, R6_
