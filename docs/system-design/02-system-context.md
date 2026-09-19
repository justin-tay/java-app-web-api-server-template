# 2. System Context

## Actors and external systems

| Actor / system | Relationship |
| --- | --- |
| Browser client | The primary consumer. Authenticates via an OIDC login redirect, then calls the API using a server-side session cookie. |
| Keycloak (OIDC Identity Provider) | The application's one external dependency. Issues identity for authentication; the application never manages passwords itself. See [Authentication](06-security/authentication.md). |
| Relational database | Stores application data (local users, groups, roles) and server-side session state. Schema is managed exclusively through Liquibase; see [Data Model](04-data-model.md). |
| Log/metrics/trace collector | The application emits ECS-structured JSON logs to stdout and OpenTelemetry traces; a deployment-provided collector is responsible for shipping, storage, and alerting. See [Logging](05-crosscutting-concepts/logging/README.md). |
| CI/CD pipeline | Runs database migrations against the target database using a separate, DDL-privileged account before an application version is deployed. See [ADR 0004](../adr/0004-database-schema-management.md). |

## Authentication and authorization split

Identity and authentication are delegated entirely to Keycloak (OIDC).
Authorization is local: once a user is authenticated, the application looks
up that user's groups and roles in its own database rather than trusting
roles asserted by the identity provider. See
[Authorization](06-security/authorization.md) and
[ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md) for
why these are split this way.

## What is out of scope for the application itself

Several concerns that a full system context diagram would show are
explicitly deployment responsibilities, not something the application
configures or runs:

- TLS termination in production deployments (the application supports TLS
  directly for local development only; see the root
  [README](../../README.md)).
- Centralized log storage, retention, and access control.
- Database backup, rollback, and the migration-runner account's credentials.
- Reverse proxy / load balancer configuration, including which client-IP
  header (if any) is trusted.

These are called out explicitly, rather than left implicit, throughout
[Security](06-security/README.md) and
[Deployment & Operations](07-deployment-operations.md).
