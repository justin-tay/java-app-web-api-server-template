# ADR 0009: Trace-correlated structured logging

**Status:** Accepted

## Decision

The application adds Micrometer Tracing with the OpenTelemetry bridge
(`micrometer-tracing-bridge-otel`, `spring-boot-micrometer-tracing-opentelemetry`) and
samples every request (`management.tracing.sampling.probability=1.0`), but exports no
spans anywhere: tracing exists only to add `trace.id`/`span.id` to structured logs, read
from the active `Tracer` span by `TracingLoggingContextFilter`, alongside the unchanged
`http.request.id` from `SecurityLoggingContextFilter`.

## Context

Spring Boot's ECS structured log formatter passes MDC entries through verbatim; it does
not rename Micrometer Tracing's own `traceId`/`spanId` MDC keys to ECS's
`trace.id`/`span.id`. `TracingLoggingContextFilter` reads the active span from `Tracer`
directly and adds it under ECS's field names instead, immediately after
`SecurityLoggingContextFilter` in the filter chain (see docs/adr/0002).

Getting a working `Tracer` bean in Spring Boot 4.1 also required
`spring-boot-micrometer-tracing-opentelemetry`, a dedicated autoconfiguration module
separate from `spring-boot-actuator-autoconfigure`; the Micrometer library dependency
alone was not sufficient to create the bean. No OTel exporter dependency is added:
Spring Boot's OTLP tracing autoconfiguration activates the moment that jar is present
regardless of endpoint configuration, defaulting to attempting `localhost:4318` on every
request, which would be noisy in every dev/CI/test environment with nothing listening.

This template's plausible caller, AWS Bedrock AgentCore, propagates W3C `traceparent`
(and `X-Amzn-Trace-Id`, and a session ID) to the tools it invokes. With tracing enabled,
this application automatically honors an incoming `traceparent`, giving free log
correlation with an AgentCore caller even with no exporter configured. Making the
application's own request appear as a span inside AgentCore's trace view requires
actually exporting, which remains deliberately out of scope. Two AWS paths exist for
that later: an ADOT Collector sidecar at `localhost:4318` (Spring Boot's OTLP default),
or direct export to `https://xray.<region>.amazonaws.com/v1/traces`, which needs AWS
SigV4 request signing that only the ADOT Java agent provides out of the box, conflicting
with this decision's library-only approach.

## Consequences

Every sampled request now carries `trace.id`/`span.id` on its structured logs; nothing
is exported anywhere today. Field contract and correlation semantics are maintained in
[`docs/security/logging/schema.md`](../security/logging/schema.md),
[`docs/security/logging/event-reference.md`](../security/logging/event-reference.md),
and [`docs/security/logging/README.md`](../security/logging/README.md).
