# ADR 0011: Trace-correlated structured logging

## Status

Accepted

## Context

Spring Boot's ECS structured log formatter passes MDC entries through verbatim; it does
not rename Micrometer Tracing's own `traceId`/`spanId` MDC keys to ECS's
`trace.id`/`span.id`. Spring Boot's own `logging.structured.json` correlation mechanism
already documents that, with Micrometer Tracing present, `traceId`/`spanId` land in MDC
on every log statement with an active span, not only ones reached through this
application's own filters, so a `StructuredLoggingJsonMembersCustomizer`
(`logging.structured.json.customizer`) is the natural place to rename them: it needs no
`Tracer` injection (these customizers are reflection-instantiated outside the Spring
`ApplicationContext`, accepting only `Environment`/`ThrowableProxyConverter` constructor
parameters) and needs no filter-chain wiring.

An earlier version of this decision added a `TracingLoggingContextFilter` that read
`Tracer.currentSpan()` directly and wrote `trace.id`/`span.id` into MDC itself,
positioned immediately after `AuthenticatedUserLoggingContextFilter`. That was replaced by the
customizer once testing confirmed Micrometer's own `traceId`/`spanId` MDC population
already covers every log statement with an active span, including ones the filter never
touched (audit and session lifecycle events), making the extra filter and its `Tracer`
dependency unnecessary.

A working `Tracer` bean also required the separate
`spring-boot-micrometer-tracing-opentelemetry` autoconfiguration module; the Micrometer
library dependency alone does not create it. No OTel exporter dependency is added:
Spring Boot's OTLP tracing autoconfiguration activates the moment that jar is present,
defaulting to attempting `localhost:4318` on every request, which would be noisy in
every dev/CI/test environment with nothing listening.

This template's plausible caller, AWS Bedrock AgentCore, propagates a W3C `traceparent`
to the tools it invokes. With tracing enabled, this application automatically honors an
incoming `traceparent`, giving free log
correlation with an AgentCore caller even with no exporter configured. Making the
application's own request appear as a span inside AgentCore's trace view requires
actually exporting, which remains deliberately out of scope: the available export paths
need either a collector sidecar or AWS SigV4 request signing that only the ADOT Java
agent provides out of the box, conflicting with this decision's library-only approach.

## Decision

The application adds Micrometer Tracing with the OpenTelemetry bridge
(`micrometer-tracing-bridge-otel`, `spring-boot-micrometer-tracing-opentelemetry`) and
samples every request (`management.tracing.sampling.probability=1.0`), but exports no
spans anywhere: tracing exists only to add `trace.id`/`span.id` to structured logs.
`TraceCorrelationJsonMembersCustomizer` renames Micrometer's own `traceId`/`spanId` MDC
entries to those ECS field names at JSON serialization time, alongside the unchanged
`http.request.id` (established by `RequestCorrelationContextFilter`; see
[ADR 0012](0012-request-correlation-ahead-of-security-chain.md)).

## Consequences

Every log statement with an active sampled span now carries `trace.id`/`span.id`, not
only ones this application's own filters touch; nothing is exported anywhere today.
Field contract and correlation semantics are maintained in
[`docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md`](../system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md),
[`docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/event-reference.md`](../system-design/08-crosscutting-concepts/06-logging-and-monitoring/event-reference.md),
and [`docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/README.md`](../system-design/08-crosscutting-concepts/06-logging-and-monitoring/README.md).
