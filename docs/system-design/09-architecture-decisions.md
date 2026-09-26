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
| [ADR 0017](../adr/0017-invalid-session-and-privilege-change-logging.md) : Log unrecognized session IDs and session privilege changes | A request presenting an unknown session ID is logged as `resume_session` with reason `session_not_found`, without logging the presented ID, and a change in a session's reloaded `ROLE_` authorities is logged once as `update_session` with reason `privilege_change`. |
| [ADR 0018](../adr/0018-development-fixtures-kept-out-of-production.md) : Development fixtures kept out of production | Development users are a Liquibase changeset that runs only in the `dev` context, and the development JWKS is a test resource that is never packaged, so a production database and artifact get neither by default. |
| [ADR 0019](../adr/0019-shared-commons-auto-configuration.md) : Shared commons auto-configuration | The repository is a Maven multi-module build: `commons` applies the shared security and logging baseline through secure-by-default auto-configuration, and each backend is an `app-*` module that depends on it. |
| [ADR 0020](../adr/0020-jwks-rotation-from-aws-secrets-manager.md) : JWKS rotation from AWS Secrets Manager | The private JWKS is a list of locations read again on a schedule, following `cdk-jwks-secret`'s rotation rules for signing, publishing, and ID token decryption; the optional `commons-aws` module reads `aws-secretsmanager:` locations with Spring Cloud AWS. |

See [docs/adr/README.md](../adr/README.md) for the criteria used to decide
whether a change warrants a new ADR.

| [ADR 0021](../adr/0021-authorisation-change-audit-log-events.md) : Audit authorisation changes with log events, not history tables | Every user, group, and role change is an ECS `iam` log event carrying the prior state, the changes, and the roles granted or withdrawn, by stored role name; no Envers tables are added, and rows record only their latest `created_by` and `updated_by`. |

<!-- arc42-manual: Add a row here when a new ADR is accepted. Do not restate an ADR's Context/Decision/Consequences in this table; link to it instead. -->
<!-- /arc42-manual -->
