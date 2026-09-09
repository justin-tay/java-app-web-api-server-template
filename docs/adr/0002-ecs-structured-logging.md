# ADR 0002: ECS structured logging

**Status:** Accepted

## Decision

The application emits structured JSON logs to standard output using Elastic Common
Schema (ECS) fields. Request lifecycle, security audit, and application lifecycle
events include a request correlation ID where applicable.

## Context

A documented standard schema makes logs machine-readable and consistently
searchable across application instances and by downstream log collectors. JSON
events avoid fragile parsing of free-form log messages.

Sensitive request content is excluded: bodies, cookies, authorization headers,
credentials, tokens, and session IDs are not logged. Known sensitive query
parameter values are redacted. Log collection, retention, access control, and
alerting are deployment-platform responsibilities.

## Consequences

New application audit events must use ECS fields and preserve the redaction
rules. The detailed field contract and operational guidance are maintained in
[`docs/security-logging.md`](../security-logging.md) and
[`docs/security-logging-schema.md`](../security-logging-schema.md).
