# 1. Introduction and Goals

<!-- arc42-generated -->
This template provides an opinionated Spring Boot Web API server that services
browser clients, with authentication delegated to an OpenID Connect identity
provider (Keycloak) and authorization resolved locally against a relational
user/group/role model. Its purpose is to give a new service a secure,
observable baseline (session handling, structured logging, error responses,
hardening) on day one, rather than each project re-deriving these concerns
from scratch.
<!-- /arc42-generated -->

## 1.1 Requirements Overview

<!-- arc42-generated -->
| Priority | Requirement | Description |
| --- | --- | --- |
| High | Browser-facing authentication | Authenticate browser users via OIDC authorization code flow against Keycloak, with `private_key_jwt` client authentication (`WebSecurityAutoConfiguration`, `PrivateKeyJwtAutoConfiguration`, `application.yaml`). |
| High | Server-side session management | Maintain server-side sessions backed by JDBC (Spring Session), with absolute and idle timeouts, single concurrent session, and audited lifecycle events. |
| High | Local authorization model | Resolve a user's roles from a locally owned user/group/role schema (`app_user`, `app_group`, `app_role`) rather than trusting identity-provider claims, refreshed on every request. |
| High | Administration API | Expose REST endpoints for administering users, groups, and roles, each individually role-gated (`/admin/users`, `/admin/groups`, `/admin/roles`). |
| Medium | Standards-based error responses | Return RFC 9457 Problem Details (`application/problem+json`) for authentication, authorization, validation, and firewall-rejected requests instead of framework default error pages. |
| Medium | Structured, trace-correlated logging | Emit Elastic Common Schema (ECS) JSON logs to stdout, correlated with OpenTelemetry trace/span identifiers. |
| Medium | Operational health signal | Expose a minimal, unauthenticated health check on a separate management port for load balancer / monitoring probes, without exposing the rest of Actuator. |
| Low | Native image compatibility | Support building the application as a GraalVM native image (`native-maven-plugin`, `ApplicationRuntimeHints`). |

See [Feature Specifications](../specifications/) for a finer-grained
requirements breakdown of individual features (currently
`user-authorisation/`).
<!-- /arc42-generated -->

## 1.2 Quality Goals

<!-- arc42-generated -->
| # | Quality Goal | Motivation | Scenario |
| --- | --- | --- | --- |
| 1 | Security | The template's entire reason to exist is to give adopters a secure-by-default starting point (authentication, session handling, headers, error responses) rather than a bare Spring Boot skeleton. | An unauthenticated request to any endpoint other than the health check or JWKS is rejected with a Problem Details response, never a stack trace or framework default page. See [Security](08-crosscutting-concepts/02-security-and-authentication/README.md). |
| 2 | Observability | Production incidents must be diagnosable from logs alone, with every log line traceable to the request and user that produced it. | Every request-scoped log line carries `http.request.id`, `trace.id`, `span.id`, and, once authenticated, the acting user, in ECS-structured JSON. See [Logging](08-crosscutting-concepts/06-logging-and-monitoring/README.md). |
| 3 | Auditability | Session-security-relevant events (login, logout, concurrent-session eviction, absolute timeout, authority change) must be reconstructable after the fact, independent of the session ID itself. | Each session lifecycle event is logged with a random, application-local audit identifier distinct from the session ID (ADR 0008), and authority changes take effect on the very next request (ADR 0015). |
| 4 | Maintainability | As a template other services are forked from, conventions must be explicit and enforced automatically rather than left to reviewer memory. | CI (`build-and-test.yml`) fails the build on formatting drift (`spring-javaformat`) and reports JaCoCo coverage on every pull request. |
| 5 | Portability | The template targets both a conventional JVM deployment and a GraalVM native image, and must not silently rely on reflection Spring cannot see. | `mvn -Pnative native:compile` succeeds using the runtime hints registered in `ApplicationRuntimeHints` and by the commons auto-configurations. |
<!-- /arc42-generated -->

## 1.3 Stakeholders

<!-- arc42-generated -->
| Role | Contact | Expectations |
| --- | --- | --- |
| Adopting development team | (project-specific) | A template they can fork and extend without re-deriving authentication, session, logging, and error-handling concerns. |
| Security / compliance reviewer | (project-specific) | Evidence that the template's security posture maps to recognized standards; see [ASVS control implementation](08-crosscutting-concepts/02-security-and-authentication/asvs.md) and the [IM8 component definitions](../standards/). |
| Operations / platform team | (project-specific) | A clear deployment contract: what the deployer must supply (database, TLS material, reverse proxy) versus what the application owns outright. See [Deployment View](07-deployment-view.md). |
<!-- /arc42-generated -->

<!-- arc42-manual: Add named stakeholders (product owner, security sign-off authority, on-call rotation) once this template is adopted by a concrete project; the codebase alone cannot identify individuals or teams. -->
<!-- /arc42-manual -->

## Scope

<!-- arc42-generated -->
This document covers the application's own architecture: how it is
structured, how it handles a request end to end, and how it is deployed and
operated. It deliberately does not duplicate material that already has a
better home:

- Durable technical decisions and their rationale live in [ADRs](../adr/),
  summarized in [Architecture Decisions](09-architecture-decisions.md).
- Feature-level requirements and design live in
  [Specifications](../specifications/).
- External standards and control catalogs kept for reference, rather than
  paraphrased, live in [Standards](../standards/).

See [References](13-references.md) for the full set of links.
<!-- /arc42-generated -->
