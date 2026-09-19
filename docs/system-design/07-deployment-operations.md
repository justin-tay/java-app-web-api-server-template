# 7. Deployment & Operations

## Ports

| Port | Purpose |
| --- | --- |
| `8081` | The application's main HTTP(S) port, serving the API and login flow. |
| `8082` | The Actuator management port, kept separate from the main application port. See [ADR 0008](../adr/0008-actuator-management-port.md) and [Hardening](06-security/hardening.md#actuator-management-port). |

## Local development

Two local run modes exist, both documented in the root
[README](../../README.md):

- `mvn -Plocal spring-boot:run` for local HTTP development, with TLS disabled
  and the session cookie marked non-secure.
- `./bin/start-api-server-tls.sh` for local TLS development, using
  certificates generated under `.local/certs`.

Both require a locally running Keycloak instance, provisioned with
`bin/configure-keycloak.js` and `bin/seed-test-data.js`; see the root
[README](../../README.md) for the exact steps.

## Configuration

Application configuration is centralized in
`src/main/resources/application.yaml` and `ApplicationProperties`. Deployment
environments are expected to override configuration through standard Spring
Boot mechanisms (profiles, environment variables), not by editing the
committed defaults.

## What deployment must provide

The template intentionally leaves several operational concerns to the
deployment environment rather than configuring them itself:

- **Database**: provisioning, credentials, backups, and the DDL-privileged
  migration account described in [ADR 0001](../adr/0001-database-schema-management.md).
- **TLS termination** in production (the application's own TLS support is a
  local-development convenience only).
- **Reverse proxy / load balancer**, including which client-IP header, if
  any, is trusted (`ClientIpResolver` defaults to trusting none).
- **Log and trace collection**: the application only writes ECS-structured
  JSON to stdout and emits OpenTelemetry traces; shipping, storage,
  retention, and alerting are downstream responsibilities. See
  [Logging](05-crosscutting-concepts/logging/README.md).
- **Secret management and rotation**, in particular the JWKS signing/
  encryption key pair; see [ADR 0006](../adr/0006-tls-and-oauth-client-key-management.md).

These are also called out individually, with an explicit "Deployment
decision required" marker, throughout the
[OWASP ASVS crosswalk](06-security/asvs.md) and
[Hardening](06-security/hardening.md).
