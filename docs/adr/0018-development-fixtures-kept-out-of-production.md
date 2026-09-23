# ADR 0018: Development fixtures kept out of production

## Status

Accepted

## Context

Two development fixtures reached every environment. The Liquibase changelog
created the enabled local users `admin`, `test-user`, and `multi-group-user`
in every database, with `admin` holding every management role through the
`Administrators` group; local users hold no credential, so whoever
authenticated at Keycloak with one of those usernames received its roles. The
development JWKS, which contains private keys, was packaged in the application
jar as `jwks.json` and loaded by default through `app.jwks:
classpath:jwks.json`, so a deployment that forgot to override `app.jwks`
silently authenticated to Keycloak with a publicly known key.

Both are needed for local development and the test suite: tests log in as the
seeded `test-user`, the local Keycloak setup (`bin/seed-test-data.js`) creates
matching accounts, and local runs need a JWKS to sign client assertions.
[ADR 0004](0004-database-schema-management.md) makes Liquibase, run by a
migration job, the sole owner of schema and reference data, and
[ADR 0007](0007-tls-and-oauth-client-key-management.md) already requires
production private keys to come from protected deployment configuration.

Liquibase applies every changeset, including one tagged with a context, when
a run requests no context at all. A plain context tag would therefore still
create the users in a production migration that sets no context.

## Decision

Development fixtures are opted into explicitly, and production gets none by
default.

- The roles and the `Administrators` group with its three management roles
  are reference data the authorization rules and the design depend on; they
  stay in `002-authorisation-seed.sql` and apply in every environment. The
  `Administrators` group has no members there.
- The development users, their memberships, and the `Test Users` group move
  to `004-development-seed.sql`, a changeset with the required context
  `@dev`. Liquibase applies it only when a run explicitly requests the `dev`
  context and skips it when no context is requested. The `local` and `test`
  profiles set `spring.liquibase.contexts: dev`, and
  `bin/start-api-server-tls.sh`, which runs without the `local` profile to
  keep TLS enabled, sets `SPRING_LIQUIBASE_CONTEXTS=dev`. A production
  migration job must not request `dev`.
- A production deployment creates its first administrator with its own
  context-restricted changeset, as described in
  [Bootstrapping the first administrator](../system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md#bootstrapping-the-first-administrator).
- The development JWKS moves to `src/test/resources/jwks.json`, which the
  build does not package. `app.jwks` has no default: `ApplicationProperties`
  requires it, so startup fails with a message naming `app.jwks` when a
  deployment does not set it. The `test` profile points it at the test
  classpath copy, and the `local` profile and `bin/start-api-server-tls.sh`
  point at the same file on disk.

## Consequences

The production jar contains no private key material, and a production
migration that requests no context creates no enabled local user. A new
production database starts with no user able to call the administration API
until the bootstrap changeset runs.

`002-authorisation-seed.sql` changed after it may have run, contrary to ADR
0004's append-only rule; this is acceptable only because the template has no
deployed database. An adopter whose database already applied the original
changeset must instead add a new changeset that deletes or disables the
development users, and mark the edited `002` as valid for their database (for
example with `validCheckSum`).

`004-development-seed.sql` is still packaged with the changelog, as inert data
that runs only on request. Developers running the application in a way that
neither activates the `local` profile nor uses `bin/start-api-server-tls.sh`
must set `app.jwks` and `spring.liquibase.contexts` themselves.
