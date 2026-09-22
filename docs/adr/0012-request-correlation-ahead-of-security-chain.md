# ADR 0012: Request correlation fields established ahead of the security chain

## Status

Accepted

## Context

Spring Security's `HttpFirewall` check runs as the first statement of
`FilterChainProxy.doFilterInternal`, before it retrieves the internal filter list that
`HttpSecurity.addFilterBefore(...)`-registered filters live in. A rejected request
therefore never reaches any filter registered that way, which is why
`http.request.id`/`source.ip`/`client.ip` were previously absent from `reject_request`
events (confirmed by reading `FilterChainProxy`'s source, not by assumption). Moving
their establishment to a filter registered directly with the servlet container, at an
order below Spring Security's own filter chain registration, makes them available
before the firewall check runs, matching how `trace.id`/`span.id` were already available
there once Micrometer Tracing's own observation filter was confirmed to run that early.

## Decision

`http.request.id`, `source.ip`, and `client.ip` are established by
`RequestCorrelationContextFilter`, a plain servlet filter registered directly with the
container ahead of `springSecurityFilterChain` (order `Ordered.HIGHEST_PRECEDENCE + 10`,
lower than Spring Security's own `-100`), rather than through `HttpSecurity`. It scopes
and removes only the three MDC entries it itself adds; it does not clear MDC on entry or
exit. `LoggingContextCleanupFilter`, registered even earlier
(`Ordered.HIGHEST_PRECEDENCE`, the outermost filter in the application), is the single
place that clears MDC entirely, once, on exit, as a safety net for a reused servlet
thread. `AuthenticatedUserLoggingContextFilter` keeps its original chain position and now
only adds `user.name` post-authentication; session-audit initialization moved to its own
filter for unrelated reasons (see
[ADR 0009](0009-session-audit-initialization-checked-every-request.md)).

## Consequences

`http.request.id`, `source.ip`, `client.ip`, `trace.id`, and `span.id` are now available
on every event this application logs, including `reject_request`; only `user.name`
remains conditional on authentication having run.

A future filter that needs to run ahead of Spring Security's chain must follow the
same pattern:

- Register it as a `FilterRegistrationBean` at an order below Spring Security's own
  (and below `LoggingContextCleanupFilter`'s `Ordered.HIGHEST_PRECEDENCE` if it adds
  MDC entries that must survive to the end of the request).
- Scope and remove only the MDC keys it adds itself, rather than blanket-clearing.
- Add it explicitly to `MockMvcITSupport` rather than assuming MockMvc will discover it.

Field contract and correlation semantics are maintained in
[`docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md`](../system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md),
[`docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/event-reference.md`](../system-design/08-crosscutting-concepts/06-logging-and-monitoring/event-reference.md),
and [`docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/README.md`](../system-design/08-crosscutting-concepts/06-logging-and-monitoring/README.md).
