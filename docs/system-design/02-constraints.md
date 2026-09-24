# 2. Constraints

## Technical Constraints

<!-- arc42-generated -->
| Constraint | Description |
| --- | --- |
| Java 17 | `pom.xml` pins `java.version` to 17; the codebase uses no preview features. |
| Spring Boot 4.1.1 | The application inherits from `spring-boot-starter-parent:4.1.1`; framework upgrades follow Spring Boot's own release cadence. |
| Maven build | The project builds with the Maven Wrapper (`mvnw`/`mvnw.cmd`), not Gradle. |
| Liquibase-owned schema | `spring.jpa.hibernate.ddl-auto: none` and `spring.session.jdbc.initialize-schema: never`; Liquibase (`db/changelog/db.changelog-master.yaml`) is the only schema owner, per [ADR 0004](../adr/0004-database-schema-management.md). Hibernate never creates or alters tables. |
| Keycloak as identity provider | The application does not implement credential storage or verification itself; Keycloak owns authentication end to end via OIDC, per [ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md). |
| JDBC-backed sessions | Sessions must be backed by a relational datastore reachable from the application (Spring Session JDBC); an in-memory or client-side session store is not an option this template supports, per [ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md). |
| `private_key_jwt` client authentication | The OAuth2 client authenticates to Keycloak using a JWKS-backed key, not a client secret. `commons.security.oauth2.jwks` has no default: every deployment supplies its own private JWKS location, and startup fails without one ([ADR 0018](../adr/0018-development-fixtures-kept-out-of-production.md)). |
| GraalVM native image support | `native-maven-plugin` is on the build; changes that rely on unregistered reflection, resources, or proxies will fail native compilation unless hints are added to `ApplicationRuntimeHints`. |
| Formatting enforced in CI | `spring-javaformat-maven-plugin` runs `apply` during `mvn verify`; CI (`build-and-test.yml`) fails the build if `git diff` shows any formatting drift afterward. |
<!-- /arc42-generated -->

## Organizational Constraints

<!-- arc42-manual: Document team structure, release cadence, budget, or process constraints for the adopting project. The codebase alone does not carry this information. -->
| Constraint | Description |
| --- | --- |
| | |
<!-- /arc42-manual -->

## Development Conventions

<!-- arc42-generated -->
| Convention | Description |
| --- | --- |
| ADR discipline | Consequential technical decisions are recorded as ADRs under `docs/adr/`, using Michael Nygard's five-part template ([ADR 0001](../adr/0001-adr-template.md)). See [docs/adr/README.md](../adr/README.md) for when a change warrants one. |
| Controlled vocabulary | `CONTEXT.md` at the repository root defines canonical terms (`Deployment decision required`, `Deployment responsibility`, `Product decision required`, `Delegated to <system>`, `Control implementation`) that documentation must use consistently instead of synonyms. |
| Control implementation documents | Standard-to-implementation mappings (ASVS, hardening, headers) live under [08-crosscutting-concepts/02-security-and-authentication/](08-crosscutting-concepts/02-security-and-authentication/README.md) and follow OSCAL component-definition terminology, per [ADR 0003](../adr/0003-control-implementation-terminology.md). |
| Sharded system design document | This document is one Markdown file per chapter under `docs/system-design/`, numeric-prefixed so reading order is visible in a plain directory listing. Each chapter file is the source of truth; there is no single-file master to keep in sync. |
| Code formatting | `spring-javaformat` is the enforced formatter; run `mvn spring-javaformat:apply` (or `mvn verify`) before committing Java changes. |
| Test coverage reporting | JaCoCo runs on every `mvn verify` and CI publishes an instruction/line/branch coverage summary on each pull request, without a hard-enforced minimum threshold at present. |
<!-- /arc42-generated -->
