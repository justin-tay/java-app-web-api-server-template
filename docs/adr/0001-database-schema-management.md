# ADR 0001: Database schema management

**Status:** Accepted

## Decision

Liquibase is the sole owner of application and Spring Session schema and reference
data. Every schema or data change is an ordered, version-controlled Liquibase
changeset in `src/main/resources/db/changelog`.

A dedicated CI migration job, using the same revision that will be deployed,
executes the changelog against the target database before the application is
released. The application runtime never creates, updates, or seeds database
objects.

## Context

The application database account deliberately does not have DDL privileges such
as `CREATE`, `ALTER`, or `DROP`. It has only the data-access permissions needed
while the service is running. A separate migration-specific account used by the
CI job has the permissions required to apply approved changesets.

This implements least privilege, prevents a compromised or faulty application
instance from changing the database structure, and keeps migrations explicit,
reviewable, and independently auditable. The deployment pipeline has a clear
failure boundary: a migration must succeed before an application version that
depends on it can run.

This rule includes Spring Session. The `SPRING_SESSION` and
`SPRING_SESSION_ATTRIBUTES` tables are defined in
`003-spring-session-schema.sql`; Spring Boot's JDBC-session schema initializer
is disabled. Hibernate DDL generation is also disabled.

Liquibase's own `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK` tables are the
only framework-managed metadata tables.

## Delivery contract

The CI migration job must:

1. Use the Liquibase changelog from the exact application revision being
   deployed.
2. Connect with the migration-specific database account.
3. Run Liquibase validation and apply pending changesets before application
   rollout.
4. Fail the release when validation or migration fails; it must not deploy the
   application afterward.

The deployed application's configuration must set `spring.liquibase.enabled`
to `false`. Test environments may apply the changelog as explicit test setup,
but production-like application startup must not apply it.

## Consequences

Changesets are append-only: do not edit a changeset that may already have run
outside a disposable local database. Use a new changeset for corrections,
schema evolution, and data migrations. A database backup and rollback plan is
required for any destructive or irreversible change.

The CI job implementation and migration connection configuration are deployment
concerns and are intentionally not embedded in the application startup path.
