# 3. Architecture Overview

## Technology stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 17 |
| Application framework | Spring Boot (`spring-boot-starter-parent`) |
| Web layer | Spring MVC (`spring-boot-starter-web`), embedded Tomcat |
| Security | Spring Security, OIDC client (`spring-boot-starter-oauth2-client`) |
| Sessions | Server-side, JDBC-backed (`spring-boot-starter-session-jdbc`); see [ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md) |
| Persistence | Spring Data JPA (`spring-boot-starter-data-jpa`) |
| Schema management | Liquibase (`spring-boot-starter-liquibase`); see [Data Model](04-data-model.md) |
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

## Request handling shape

Requests pass through, in order: Tomcat, Spring Security's `HttpFirewall`
(rejecting malformed requests before they reach any filter), the
authentication/session/authorization filter chain, then Spring MVC
controllers in `api`. Errors at any stage are normalized to RFC 9457 Problem
Details rather than leaking stack traces or framework-specific error pages;
see [Error responses](06-security/error-responses.md) for the full mapping and
[ADR 0013](../adr/0013-rfc-9457-problem-details.md) for why.

This is a deliberately conventional layered structure for a single
deployable service, not a set of independently deployable modules; there is
one Spring Boot application (`Application.java`), one database, and one
build artifact.
