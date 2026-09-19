# 4. Data Model

## Application entities

Local authorization data lives under `domain`:

| Entity | Purpose |
| --- | --- |
| `AppUser` | A local user, matched to Keycloak's `preferred_username` claim. Authentication is delegated to Keycloak; this entity exists so authorization does not have to trust the identity provider's own role claims. |
| `AppGroup` | A group a user belongs to. |
| `AppRole` | A role, granted through group membership. |
| `AbstractAuditableEntity` | Common auditing fields (created/modified metadata) shared by the auditable entities above. |

See [Authorization](06-security/authorization.md) for how these are used to
make access-control decisions, and
[ADR 0004](../adr/0004-keycloak-authentication-local-authorisation.md) for
why authentication and authorization are split this way.

## Session state

Server-side sessions are persisted in the database via Spring Session JDBC,
in the `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES` tables. This is
schema Liquibase owns explicitly (`003-spring-session-schema.sql`); Spring
Boot's own JDBC-session schema initializer is disabled. See
[Sessions](06-security/sessions.md) and
[ADR 0005](../adr/0005-jdbc-backed-server-side-sessions.md).

## Schema ownership

Liquibase is the sole owner of both application and Spring Session schema
and reference data. Every schema or data change is an ordered,
version-controlled changeset in `src/main/resources/db/changelog`, applied
by a dedicated CI migration job using a database account with DDL
privileges. The application's own runtime database account has only
data-access permissions and never creates, alters, or drops schema; Hibernate
DDL generation is disabled. See [ADR 0001](../adr/0001-database-schema-management.md)
for the full rationale and the delivery contract this imposes on CI.

The template does not prescribe a specific production database product; H2
is used only for the test scope. Choosing and configuring the production
database, and the CI migration job that applies changesets to it, are
deployment decisions.
