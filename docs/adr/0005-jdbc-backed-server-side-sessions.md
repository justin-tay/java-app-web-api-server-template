# ADR 0005: JDBC-backed server-side sessions

**Status:** Accepted

## Decision

The application uses Spring Session with JDBC-backed server-side sessions. The
browser receives only the `id` session cookie; session state, including OAuth2
login state, is stored in the `SPRING_SESSION` tables.

## Context

Server-side session storage keeps authentication and OAuth2 state out of the
cookie and permits session persistence to use the application's database
platform. The session schema is version-controlled in Liquibase under ADR 0001,
not created by Spring Boot at runtime.

## Consequences

The cookie is `HttpOnly`, `SameSite=Lax`, and `Secure` outside local and test
profiles. Cookie-only tracking is used, with a 15-minute inactivity timeout and
a 12-hour absolute timeout. One concurrent session is allowed per user; a new
login invalidates that user's existing session. Changes to the Spring Session
JDBC schema must be represented by a new Liquibase changeset and remain
compatible with the library version.
