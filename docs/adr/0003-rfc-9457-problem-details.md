# ADR 0003: RFC 9457 Problem Details API errors

**Status:** Accepted

## Decision

HTTP API errors use RFC 9457 Problem Details with the
`application/problem+json` media type. The `type` URI is the stable,
machine-readable error identifier; HTTP status, title, detail, and permitted
extensions provide supporting information.

## Context

A standard error format gives clients one predictable contract for validation,
authorization, routing, domain, upstream, and unexpected failures. It avoids
endpoint-specific error envelopes and prevents internal implementation details
from being returned to callers.

## Consequences

Existing problem type URIs are public compatibility contracts and must not be
renamed after clients consume them. New error mappings must use a defined type
and must not expose stack traces, credentials, or upstream response details.
The type catalogue is maintained in
[`docs/security-error-responses.md`](../security-error-responses.md).
