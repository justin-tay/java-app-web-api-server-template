# ADR 0035: Module schemas as a changelog and as SQL, with UUID keys

## Status

Accepted

Extends [ADR 0004](0004-database-schema-management.md), which makes Liquibase the sole owner of the schema.

## Context

The schema of `commons` and `commons-accounts` was a set of Liquibase formatted-SQL files
numbered `001` to `014` in `db/changelog`, with the numbers interleaved across the two
modules and the application so that only the application's master changelog gave them
meaning. Three things were wrong with that:

* The files were written in H2's dialect. The template must run on H2, PostgreSQL and
  SQL Server, and the dialects differ in ways that matter: `TIMESTAMP` is a row version
  on SQL Server, there is no `BOOLEAN` or `CLOB`, Liquibase's generic `blob` is a large
  object on PostgreSQL where Spring expects `bytea`, and date arithmetic differs.
* A library that ships only a Liquibase changelog forces Liquibase on every consumer.
  Spring's own libraries ship plain SQL scripts per database that any migration tool can
  run.
* Identifiers were `CHAR(36)` text, which spends 36 bytes per key and foreign key and
  gives a random UUID's poor insert locality.

## Decision

Each module ships its schema in its own package, as both a Liquibase changelog and plain
SQL:

* `commons`: `com/example/commons/session/jdbc/` for Spring Session's tables, a copy of its
  schema with a wider `SESSION_ID`, and `com/example/commons/session/oidc/jdbc/` for the OIDC
  session registry's table, which only an application using back-channel logout needs.
* `commons-accounts`: `com/example/commons/accounts/jdbc/` for users, groups, roles,
  passkeys, the audit trail, the settings, tasks and review items.

In each folder `schema.yaml` is the source, written in Liquibase change types so that each
database receives its own type. `schema-h2.sql`, `schema-postgresql.sql` and
`schema-sqlserver.sql` are generated from it and committed, so a consumer who does not use
Liquibase applies the script for their database. A test fails when a script differs from
what Liquibase generates, and `-Dschema.regenerate=true` rewrites them. A consumer uses
one of the two forms, not both.

The application's master changelog includes the two module changelogs and then holds only
what the application owns: `reference-data.sql` (roles and groups, in every environment)
and `development-seed.sql` (the `dev` and `demo` fixtures, [ADR 0018](0018-development-fixtures-kept-out-of-production.md)).
File names carry no sequence; the include order in the master changelog is the only order.
A changeset's author is the module that owns it (`commons`, `commons-accounts`, `app`).
The binary column type is a per-database Liquibase property because the generic type is
wrong on PostgreSQL. Native-image hints register the module resources.

Identifiers are UUIDs in the database's own UUID type and `java.util.UUID` in Java, and
the application generates them as random version 4 values with Hibernate's
`@UuidGenerator`, so the same behaviour holds on every database and an identifier is known
before the insert. Identifiers are returned by the API, so a version 4 value is used
because a version 7 value embeds its creation time. The audit table's `target_id` stays
text because its target is not always a user.

## Consequences

The schema is written once and verified in H2 by the test suite. The PostgreSQL and SQL
Server scripts are checked offline against Liquibase's own type mappings, and by
`SchemaScriptsContainerTest`, which applies the create and drop scripts on PostgreSQL 17
and SQL Server 2022 containers, writes and reads back a user and a passkey credential, and
creates the schema again after the drop. That test is skipped when Docker is not available,
so an environment without Docker does not exercise those two scripts. It checks the SQL
itself, not Hibernate's validation of the entity mappings against those databases.

The changesets were consolidated into one per module before any deployment, so their
identifiers and checksums changed; a database that applied the earlier numbered files
cannot be upgraded by Liquibase and must be recreated.

Known limits on SQL Server: columns are `varchar`, so text outside the database collation's
code page is not preserved unless the database uses a UTF-8 collation; and the passkey
tables' 1000-character keys exceed SQL Server's 900-byte clustered key limit, which it
accepts with a warning. A deployment that needs to keep text exact should review those
columns.

Random identifiers insert at scattered positions in a primary key index. For the tables
here that is negligible until a table, such as the audit table, grows far beyond memory.
A deployment with that write volume can switch an entity to
`@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` and accept that the identifier
then discloses its creation time. That helps on PostgreSQL only: a SQL Server
`uniqueidentifier` compares its last bytes first, so version 7 values still insert at
scattered positions there. On SQL Server the options are a clustered index on a time
column with a nonclustered primary key, or identifiers generated in SQL Server's byte order.
