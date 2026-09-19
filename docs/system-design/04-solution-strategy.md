# 4. Solution Strategy

This is the connective thread behind [Architecture Decisions](09-architecture-decisions.md): why the template's most consequential decisions
reinforce rather than merely coexist with each other. Each decision's own
rationale and trade-offs live in its ADR, linked below; this section states
only the "why together."

The template exists to give a new project a defensible starting point for
authentication, authorization, sessions, logging, and API error handling
(see [Introduction & Goals](01-introduction.md#purpose)), so its strategy
follows directly from that goal: **make the parts of a web API that are
easy to get wrong, and expensive to fix later, the template's job**, and
leave product- and deployment-specific choices explicitly open rather than
guessing at them.

Three decisions carry out that split for security:

- Authentication is delegated entirely to Keycloak via OIDC, while
  authorization (groups, roles) is looked up locally rather than trusted
  from identity-provider claims
  ([ADR 0005](../adr/0005-keycloak-authentication-local-authorisation.md)).
  This means the template never owns credentials, but still owns and can
  change access control independently of identity-provider configuration.
- Sessions are server-side and JDBC-backed rather than client-held tokens
  ([ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md)), so a
  session can be revoked immediately rather than only expiring. Authorities
  are also reloaded from the database on every request rather than cached
  at login ([ADR 0015](../adr/0015-per-request-local-authority-refresh.md)),
  so an authorization-relevant change takes effect on the next request, not
  the next login.
- The application's own database account has no DDL privileges; Liquibase,
  run by a separate CI account, is the sole owner of schema
  ([ADR 0004](../adr/0004-database-schema-management.md)). This keeps a
  compromised or buggy application process from being able to alter its
  own data model.

A second pair of decisions carries the same "make it observable and
diagnosable by default" strategy through logging and error handling: all
logs are structured, ECS-formatted JSON correlated with distributed traces
([ADR 0010](../adr/0010-ecs-structured-logging.md),
[ADR 0011](../adr/0011-trace-correlated-structured-logging.md)), established
ahead of the security filter chain so even a request Spring Security itself
rejects is still correlated
([ADR 0012](../adr/0012-request-correlation-ahead-of-security-chain.md)); and
every API error, from validation failures to firewall rejections, is
normalized to RFC 9457 Problem Details with a stable `type` URI rather than
a framework-specific or ad hoc error shape
([ADR 0013](../adr/0013-rfc-9457-problem-details.md)).

Finally, the template applies the same "leave nothing implicit" strategy to
itself: its own documentation is a sharded, chapter-per-file design document
with the shard files as the only hand-maintained source of truth
([ADR 0002](../adr/0002-system-design-document.md)), so both a human
adopter and an AI coding agent extending the template can find the current
state of any one concern without reading the whole codebase.
