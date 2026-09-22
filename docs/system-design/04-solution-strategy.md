# 4. Solution Strategy

## Technology Decisions

<!-- arc42-generated -->
| Decision Area | Choice | Rationale |
| --- | --- | --- |
| Language / runtime | Java 17, GraalVM native image support | Long-term-support Java baseline; native image reduces cold-start and memory footprint for adopters who need it, without being mandatory. |
| Framework | Spring Boot 4.1.1 (Web MVC, Security, Data JPA, Session JDBC, Actuator, Validation) | Mature, widely adopted ecosystem covering every concern this template needs (web, security, persistence, session, observability) without extra glue code. |
| Persistence | Spring Data JPA over a JDBC relational database, schema owned exclusively by Liquibase | Keeps the runtime account free of DDL privileges and makes schema evolution reviewable and repeatable independent of application deploys ([ADR 0004](../adr/0004-database-schema-management.md)). |
| Session store | Spring Session JDBC (`spring-boot-starter-session-jdbc`) | Server-side sessions survive application restarts and support horizontal scaling without sticky sessions, at the cost of a database round-trip per request ([ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md)). |
| Authentication | OIDC via Keycloak, `spring-boot-starter-oauth2-client`, `private_key_jwt` | Delegates credential handling entirely to a purpose-built identity provider; `private_key_jwt` avoids a long-lived shared secret ([ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md), [ADR 0007](../adr/0007-tls-and-oauth-client-key-management.md)). |
| Authorization | Local user/group/role model, refreshed every request | Keeps authorization changes (disable a user, change a role) effective immediately without depending on identity-provider claim refresh or token lifetime ([ADR 0015](../adr/0015-per-request-local-authority-refresh.md)). |
| Observability | Micrometer Tracing + OpenTelemetry bridge, ECS-structured JSON logs to stdout | Every log line is trace-correlated and machine-parseable without adopting a specific log backend up front ([ADR 0010](../adr/0010-ecs-structured-logging.md), [ADR 0011](../adr/0011-trace-correlated-structured-logging.md)). |
| Error responses | RFC 9457 Problem Details (`ApiResponseEntityExceptionHandler`, `ProblemDetailErrorController`, `ProblemTypes`) | A single, standards-based error shape across validation, authentication, authorization, firewall rejection, and unhandled exceptions ([ADR 0013](../adr/0013-rfc-9457-problem-details.md)). |
| Messaging | None | No message broker or asynchronous integration exists in the current scope; `AsyncConfiguration` covers in-process `@Async` only. |
<!-- /arc42-generated -->

## Quality Goal Strategies

<!-- arc42-generated -->
| Quality Goal | Approach |
| --- | --- |
| Security | Authentication and credential handling delegated to Keycloak; a locked-down `Content-Security-Policy`, `Referrer-Policy`, and `Permissions-Policy` on every response ([`WebSecurityConfiguration`](../../src/main/java/com/example/app/web/server/config/WebSecurityConfiguration.java)); per-endpoint role/authority checks; RFC 9457 error responses that never leak stack traces. Full control-to-implementation mapping: [ASVS](08-crosscutting-concepts/02-security-and-authentication/asvs.md), [Hardening](08-crosscutting-concepts/02-security-and-authentication/hardening.md), [Headers](08-crosscutting-concepts/02-security-and-authentication/headers.md). |
| Observability | A request-correlation filter registered ahead of the security filter chain guarantees `http.request.id`/`source.ip`/`client.ip` are present even for requests the `HttpFirewall` rejects before security filters run ([ADR 0012](../adr/0012-request-correlation-ahead-of-security-chain.md)). All logs are ECS JSON, trace-correlated. |
| Auditability | Session lifecycle events (creation, logout, concurrent-session eviction, absolute timeout, authority-triggered revocation) are logged through `SessionLifecycleAuditLogger` with a random per-session audit identifier distinct from the session ID itself, checked on every request rather than a single creation event, because no single Spring event covers every path a session can be created through ([ADR 0008](../adr/0008-session-lifecycle-audit-identifiers.md), [ADR 0009](../adr/0009-session-audit-initialization-checked-every-request.md)). |
| Maintainability | CI enforces formatting (`spring-javaformat`) and reports coverage on every pull request; ADRs capture the rationale behind decisions so future changes do not have to be re-derived from code alone. |
| Portability | `ApplicationRuntimeHints` registers the reflection/resource hints Spring's own AOT processing cannot infer, keeping the GraalVM native image build path exercised rather than aspirational. |
<!-- /arc42-generated -->

## Organizational Decisions

<!-- arc42-manual: Document delegation of ownership (which team owns the Keycloak realm, which team owns the database, third-party vendor decisions) once this template is adopted by a concrete project. -->
<!-- /arc42-manual -->
