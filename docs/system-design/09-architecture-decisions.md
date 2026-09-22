# 9. Architecture Decisions

This is a short summary of the template's most consequential architecture
decisions. Each row is deliberately brief; the full rationale,
alternatives considered, and consequences live in the linked ADR, not here.
Do not duplicate an ADR's content into this table when adding a new one.

| Decision | Summary |
| --- | --- |
| [ADR 0001](../adr/0001-adr-template.md) : ADR template | Records Nygard's canonical five-part ADR shape (Title/Status/Context/Decision/Consequences) as this repository's deliberate template, rather than a lighter one some tooling defaults to. |
| [ADR 0002](../adr/0002-system-design-document.md) : System design document under docs/system-design | Adopts arc42, sharded one file per chapter under `docs/system-design/`, as the source of truth; ADRs are linked from Section 9 rather than restated. |
| [ADR 0003](../adr/0003-control-implementation-terminology.md) : "Control implementation" terminology | Standard-to-status mapping documents use OSCAL's "control implementation" term, matching how this template is architecturally closer to an OSCAL component than a full System Security Plan. |
| [ADR 0004](../adr/0004-database-schema-management.md) : Database schema management | Liquibase is the sole owner of schema; the application's runtime database account has no DDL privileges, migrations run as a separate CI step. |
| [ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md) : Keycloak authentication, local authorisation | Keycloak is the identity/credential authority via OIDC; authorization (groups/roles) is looked up locally rather than trusted from identity-provider claims. |
| [ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md) : JDBC-backed server-side sessions | Sessions are server-side and JDBC-backed via Spring Session; the browser holds only an opaque session ID cookie. |
| [ADR 0007](../adr/0007-tls-and-oauth-client-key-management.md) : TLS and OAuth client key management | Production uses TLS 1.2/1.3 with strong cipher suites; the OAuth2 client authenticates to Keycloak with `private_key_jwt`. |
| [ADR 0008](../adr/0008-session-lifecycle-audit-identifiers.md) : Session lifecycle audit identifiers | Session-security lifecycle events use a random, application-local audit identifier rather than the session ID itself. |
| [ADR 0009](../adr/0009-session-audit-initialization-checked-every-request.md) : Session audit initialization checked every request | Session-creation auditing uses an idempotent, unconditional per-request check rather than a single trigger event, because no single Spring event covers every way a session can be created. |
| [ADR 0010](../adr/0010-ecs-structured-logging.md) : ECS structured logging | All logs are structured JSON on Elastic Common Schema (ECS) fields, emitted to stdout. |
| [ADR 0011](../adr/0011-trace-correlated-structured-logging.md) : Trace-correlated structured logging | OpenTelemetry tracing (via Micrometer Tracing) is added, with trace identifiers correlated into the structured logs from ADR 0010. |
| [ADR 0012](../adr/0012-request-correlation-ahead-of-security-chain.md) : Request correlation ahead of the security chain | Request correlation fields (`http.request.id`, `source.ip`, `client.ip`) are established by a plain servlet filter registered before the security filter chain, not inside it. |
| [ADR 0013](../adr/0013-rfc-9457-problem-details.md) : RFC 9457 Problem Details | API errors use RFC 9457 Problem Details (`application/problem+json`) with a stable `type` URI, not framework-default error pages. |
| [ADR 0014](../adr/0014-actuator-management-port.md) : Actuator management port | Actuator runs on a separate management port, exposing only a minimal unauthenticated health check publicly. |
| [ADR 0015](../adr/0015-per-request-local-authority-refresh.md) : Per-request local authority refresh | Authorities are reloaded from the local user/group/role model on every request rather than cached from login, so authorization-relevant changes take effect immediately; `SessionRevocationService` is kept for prompt, audit-visible termination on disable/delete/membership change. |
| [ADR 0016](../adr/0016-security-documentation-under-crosscutting-concepts.md) : Security documentation under Crosscutting Concepts | Security documents are a subsection of Crosscutting Concepts rather than their own top-level chapter, because they are control implementations (standard-to-status mappings), not an architectural view. |

See [docs/adr/README.md](../adr/README.md) for the criteria used to decide
whether a change warrants a new ADR.

<!-- arc42-manual: Add a row here when a new ADR is accepted. Do not restate an ADR's Context/Decision/Consequences in this table; link to it instead. -->
<!-- /arc42-manual -->
