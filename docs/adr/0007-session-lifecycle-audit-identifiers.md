# ADR 0007: Separate session lifecycle audit identifiers

**Status:** Accepted

## Decision

Session-security lifecycle logs use a randomly generated, application-local
audit identifier stored as a server-side Spring Session attribute. It is emitted
as ECS `session.id`; the browser's `id` cookie value and Spring Session's raw
session ID are never logged.

## Context

Session lifecycle monitoring needs to correlate creation, session-fixation
renewal, logout, absolute-timeout, and concurrent-session expiry events. The
session credential is authentication material and must not enter logs. A shared
HMAC-based correlation value would require every downstream system to hold the
same secret, which is not appropriate for this template.

## Consequences

`SessionLifecycleAuditLogger` generates a UUID only after an application
session exists and records it only on lifecycle events, rather than every HTTP
request. It survives a Spring Security session-ID rotation, allowing those
events to be correlated as one logical login. It is still pseudonymous security
telemetry, so log retention and access controls apply. Cross-system session
correlation is intentionally not provided.

The application logs only lifecycle causes it can know accurately. JDBC expiry
cleanup and arbitrary invalid-cookie attempts do not currently provide a
reliable, correlated Spring hook; anomaly detection remains a separately
designed operational capability.
