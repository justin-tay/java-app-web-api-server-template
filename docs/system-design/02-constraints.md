# 2. Constraints

Conditions the design must work within, rather than choices made freely
during design.

## Technical constraints

| Constraint | Source |
| --- | --- |
| Java 17 | Pinned as `java.version` in `pom.xml`; the whole toolchain (Spring Boot, dependencies, CI) targets this version. |
| Spring Boot / Spring Security / Spring Data JPA / Spring Session | The template's implementation stack; see [Building Block View](05-building-block-view.md#technology-stack). |
| Single deployable service | One Spring Boot application, one database, one build artifact — not a set of independently deployable modules. See [Building Block View](05-building-block-view.md#code-organization). |
| Keycloak as the only supported identity provider | Authentication is an OIDC relying-party integration built and tested against Keycloak specifically, not a generic OIDC-provider abstraction. See [Authentication](08-crosscutting-concepts/security/authentication.md) and [ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md). |
| No DDL privileges for the application's own database account | Liquibase, run by a separate CI migration account, is the sole owner of schema; the running application cannot alter its own schema. See [ADR 0004](../adr/0004-database-schema-management.md). |

## Organizational and process constraints

| Constraint | Source |
| --- | --- |
| Built to be forked, not deployed as-is | The template deliberately leaves several decisions (deployment topology, MFA policy, production database choice, and others) to the adopting team rather than choosing on their behalf. See [Introduction & Goals](01-introduction.md#purpose). |
| Apache License 2.0 | Governs the template's own source; see [LICENSE](../../LICENSE). |
| Third-party standard text under a different license | Requirement text reproduced from OWASP ASVS in [ASVS](08-crosscutting-concepts/security/asvs.md) is licensed CC BY-SA 4.0 by OWASP, not by this project's own Apache License 2.0. |
| Documentation shard files are the source of truth | `docs/system-design/` is hand-maintained as numbered chapter files; any future single-file or PDF export is a generated artifact, never edited directly. See [ADR 0002](../adr/0002-system-design-document.md). |
