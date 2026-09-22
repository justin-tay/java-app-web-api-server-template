# 12. Glossary

<!-- arc42-generated -->
| Term | Definition |
| --- | --- |
| AppUser / AppGroup / AppRole | The three local authorization entities. A user belongs to groups; a group is granted roles; a user's effective authorities are the union of the roles of all their groups. There is no direct user-to-role assignment. |
| Control implementation | A document or section that maps an external standard's or catalog's requirements (OWASP ASVS, a cheat sheet, IM8 catalogs) to this template's actual implementation status, per requirement. Named after OSCAL's Component Definition model. See [ADR 0003](../adr/0003-control-implementation-terminology.md). |
| Delegated to \<system\> | A capability the application never implements at all; a specific external system (currently only Keycloak) owns it entirely, and there is no decision left for the application or its deployer to make. |
| Deployment decision required | A one-time infrastructure, topology, or configuration choice that the team deploying this application must actively make; the template deliberately leaves it unset (for example, the production database product or TLS termination point). |
| Deployment responsibility | An ongoing operational duty the deploying environment must carry out continuously (for example, restricting the management port to the health-check network, shipping stdout logs), as distinct from a one-time deployment decision. |
| Product decision required | A business or feature-policy choice (for example, when to require reauthentication) that is independent of infrastructure, and that this template does not make on the adopter's behalf. |
| ECS (Elastic Common Schema) | The structured logging field schema this application emits as JSON to stdout. See [ADR 0010](../adr/0010-ecs-structured-logging.md) and [Logging](08-crosscutting-concepts/06-logging-and-monitoring/schema.md). |
| `private_key_jwt` | An OAuth2 client authentication method where the client proves its identity with a signed JWT (backed by the JWKS at `app.jwks`) instead of a shared client secret. See [ADR 0007](../adr/0007-tls-and-oauth-client-key-management.md). |
| Problem Details (RFC 9457) | The standardized `application/problem+json` error response shape this API uses for every error condition (validation, authentication, authorization, firewall rejection). See [ADR 0013](../adr/0013-rfc-9457-problem-details.md). |
| Session lifecycle audit identifier | A random, application-local identifier assigned to a session for audit logging, distinct from the session ID itself, so audit trails do not double as a session-hijacking target. See [ADR 0008](../adr/0008-session-lifecycle-audit-identifiers.md). |
| Local authority refresh | The per-request reload of a user's authorities from the local database, replacing any cached value from login, so authorization changes take effect immediately. See [ADR 0015](../adr/0015-per-request-local-authority-refresh.md). |
| Management port | The separate embedded server port (8082, base path `/app`) that exposes only an unauthenticated health check, kept apart from the application port (8081) that serves all business traffic. See [ADR 0014](../adr/0014-actuator-management-port.md). |
| OIDC back-channel logout | Server-to-server logout notification from Keycloak to this application, used when a session must be terminated without a browser round trip (for example, an administrator forcing logout in Keycloak). |
<!-- /arc42-generated -->

<!-- arc42-manual: Add domain terms specific to an adopting project's business domain once this template is extended beyond its current user/group/role scope. -->
<!-- /arc42-manual -->
