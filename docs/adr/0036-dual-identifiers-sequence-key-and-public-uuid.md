# ADR 0036: Dual identifiers, a sequence key and a public UUID

## Status

Accepted

Supersedes the identifier paragraph of [ADR 0035](0035-module-schemas-as-changelog-and-sql.md),
which made a random UUID the primary key. The rest of ADR 0035 stands.

## Context

ADR 0035 used one random version 4 UUID as both the primary key and the identifier the API
returns. That is simple, but SQL Server is a first-class target here and it makes the primary
key the clustered index by default. A random 16-byte clustered key inserts at scattered
positions in the table itself, and every foreign key and every nonclustered index entry
carries the key, so it costs 16 bytes where a `bigint` costs 8. Version 7 UUIDs do not help
there, because a SQL Server `uniqueidentifier` compares its last bytes first, and they
disclose their creation time. Judging each table on its expected size is a decision that
every adopter would repeat for every table they add, and a rule that is uniform is easier
to hold than one that has exceptions.

Options considered:

* **One random UUID as the key** (ADR 0035). Least code, and no second identifier. The cost
  above falls on SQL Server for every table, not only the large ones.
* **One time-ordered UUID.** Fixes insert locality on PostgreSQL only, and the identifier
  discloses when the entity was created.
* **A random UUID key, with the clustered index on `created_at` on SQL Server.** Keeps one
  identifier, but `created_at` is not unique, so SQL Server adds a hidden 4-byte value to the
  clustered key, every nonclustered index carries a key of about 14 bytes, and the change is
  specific to one database.
* **A `bigint` key and a public UUID.** Chosen. The clustered key is narrow, unique and
  ever-increasing on every database.

## Decision

Every entity has two identifiers, in `AbstractIdentifiedEntity`:

* `id` is the primary key, a `bigint` from a sequence, one sequence per table, named as
  Hibernate names the sequence of an entity (`app_user_seq`) and stepping by 50, which is
  what Hibernate's default pooled generator expects. It is used for joins and foreign keys
  and is never returned by the API or written to the audit trail or the logs.
* `publicId` is a `UUID` in the database's UUID type, `NOT NULL` and unique. The application
  generates it as a random version 4 value when the entity is constructed, so it is known
  before the insert, cannot be guessed, and does not disclose when the entity was created.
  The API, the audit trail's `target_id`, the logs and the passkey user handle use it.

A JSON `id` in a response is the `publicId`, and a path variable or request body that names an
entity carries a `publicId`. A service resolves it with `findByPublicId` and from then on works
with the `bigint`. A response record has no `Long` component. Entities are never serialised, so
they carry no JSON annotations.

All six entities that the API exposes have both identifiers: `AppUser`, `AppGroup`, `AppRole`,
`AccountAuditEvent`, `Task` and `ReviewItem`. The join tables `app_user_group` and
`app_group_role` have composite keys of the `bigint` identifiers and no identifier of their
own. `app_setting` has a `bigint` key only, with its name unique, because nothing addresses a setting by id and its name appears in no URL. The passkey tables keep Spring Security's keys,
and their user handle is the user's `publicId`. `review_item` keeps the user's `publicId`, not
a foreign key, so an item outlives a removed account as before, and it refers to its task by
the task's `bigint` key.

The sequences start at 1000, so an application can seed rows with fixed ids below 900, as
`reference-data.sql` and `development-seed.sql` do, giving each row its fixed `publicId` too.

## Consequences

The clustered key of every table is an 8-byte, unique, increasing number on SQL Server, and
foreign keys and secondary indexes are half the width they were. On PostgreSQL the key is
also smaller, and the choice costs nothing.

An entity has two identifiers and the rule above has to be kept: the `bigint` stays inside the
service and the persistence layer, and review enforces it. Each table gains a column and a
unique index. `account_audit_event` is the table most likely to grow without bound, and its
unique index on `public_id` still receives random inserts. The index is nonclustered and
narrower than the table, so the table itself appends in order, but a deployment with very high
write volume to it should still plan for that index.

A sequence with a pooled optimizer keeps JDBC insert batching and gives an entity its key
before the insert. Hibernate allocates 50 numbers at a time, so numbers have gaps after a
restart and carry no meaning.

The changesets were consolidated before any deployment, as in ADR 0035, so a database that
applied the previous script cannot be upgraded by Liquibase and must be recreated.
`SchemaScriptsContainerTest` is skipped without Docker, so the PostgreSQL and SQL Server
scripts for the new sequences and columns were checked against Liquibase's own type mappings
and not applied to those databases in this change.
