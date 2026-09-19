# 5. Building Block View

## Technology stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 17 |
| Application framework | Spring Boot (`spring-boot-starter-parent`) |
| Web layer | Spring MVC (`spring-boot-starter-web`), embedded Tomcat |
| Security | Spring Security, OIDC client (`spring-boot-starter-oauth2-client`) |
| Sessions | Server-side, JDBC-backed (`spring-boot-starter-session-jdbc`); see [ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md) |
| Persistence | Spring Data JPA (`spring-boot-starter-data-jpa`) |
| Schema management | Liquibase (`spring-boot-starter-liquibase`); see [Data model](#data-model) below |
| Observability | Micrometer Tracing with the OpenTelemetry bridge, Spring Boot Actuator |
| Validation | Bean Validation (`spring-boot-starter-validation`) |
| Test database | H2 (test scope only; the template does not prescribe a production database) |

## Code organization

The application lives under `com.example.app.web.server`, split by concern
rather than by feature:

| Package | Responsibility |
| --- | --- |
| `config` | Cross-cutting Spring configuration: web security wiring, Tomcat, async, REST client, application properties, time. |
| `security.authentication` | OIDC login integration with Keycloak. |
| `security.authorization` | Local user/group/role lookup and enforcement. |
| `security.session` | Server-side session lifecycle: creation, timeout, expiry, fixation renewal, logout, and their audit logging. |
| `security.firewall` | Rejection handling for requests Spring Security's `HttpFirewall` blocks before they reach the application. |
| `logging` | Request correlation and structured (ECS) logging context. |
| `domain` | JPA entities and repositories for local users, groups, and roles. |
| `api` | HTTP endpoints and API-wide error handling (`ApiResponseEntityExceptionHandler`, RFC 9457 problem types). |
| `api.admin` | Administrative endpoints. |
| `validation` | Custom Bean Validation constraints. |

This is a deliberately conventional layered structure for a single
deployable service, not a set of independently deployable modules; there is
one Spring Boot application (`Application.java`), one database, and one
build artifact. See [Runtime View](06-runtime-view.md) for how a request
actually moves through these packages.

## Data model

Local authorization data lives under `domain`:

| Entity | Purpose |
| --- | --- |
| `AppUser` | A local user, matched to Keycloak's `preferred_username` claim. Authentication is delegated to Keycloak; this entity exists so authorization does not have to trust the identity provider's own role claims. |
| `AppGroup` | A group a user belongs to. |
| `AppRole` | A role, granted through group membership. |
| `AbstractAuditableEntity` | Common auditing fields (created/modified metadata) shared by the auditable entities above. |

See [Authorization](08-crosscutting-concepts/security/authorization.md) for how these are used to
make access-control decisions, and
[ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md) for
why authentication and authorization are split this way.

### Session state

Server-side sessions are persisted in the database via Spring Session JDBC,
in the `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES` tables. This is
schema Liquibase owns explicitly (`003-spring-session-schema.sql`); Spring
Boot's own JDBC-session schema initializer is disabled. See
[Sessions](08-crosscutting-concepts/security/sessions.md) and
[ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md).

### Schema ownership

Liquibase is the sole owner of both application and Spring Session schema
and reference data. Every schema or data change is an ordered,
version-controlled changeset in `src/main/resources/db/changelog`, applied
by a dedicated CI migration job using a database account with DDL
privileges. The application's own runtime database account has only
data-access permissions and never creates, alters, or drops schema; Hibernate
DDL generation is disabled. See [ADR 0004](../adr/0004-database-schema-management.md)
for the full rationale and the delivery contract this imposes on CI.

The template does not prescribe a specific production database product; H2
is used only for the test scope. Choosing and configuring the production
database, and the CI migration job that applies changesets to it, are
deployment decisions.
