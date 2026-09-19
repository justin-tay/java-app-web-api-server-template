# ADR 0010: Request correlation fields established ahead of the security chain

**Status:** Accepted

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
filter for unrelated reasons (see docs/adr/0011).

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

Three problems surfaced while making this change, all now covered by regression tests:

- `RequestCorrelationContextFilter` originally cleared MDC on entry as well as exit,
  mirroring `AuthenticatedUserLoggingContextFilter`'s old behavior. Since it now runs after
  Micrometer's own observation filter, that entry-clear wiped `traceId`/`spanId` before
  a firewall-rejected request ever got a chance to log with them.
- Even clearing MDC only on exit was still the wrong place for a blanket clear: this
  filter's own `finally` runs while `webMvcObservationFilter` (tracing) is still
  mid-cleanup, since that filter wraps around it, so it wasn't actually the last thing
  to run before control returned to the container. A blanket clear also duplicated
  cleanup that every MDC-adding component already does for its own keys via
  try-with-resources or explicit `remove()`. The fix was to stop blanket-clearing here
  at all, and add `LoggingContextCleanupFilter` as a dedicated, genuinely outermost
  filter whose only job is one unconditional `MDC.clear()` in a `finally` around the
  entire chain: single responsibility, and its cleanup is provably the last thing that
  runs regardless of what any inner layer, including ones this application does not
  control, left behind.
- `ProblemDetailRequestRejectedHandler` used to add `source.ip` explicitly, since MDC
  was previously empty at that point. With `RequestCorrelationContextFilter` now always
  populating it first, that explicit addition became a duplicate key, which Spring
  Boot's structured logging rejects with a hard `IllegalStateException` that crashes the
  console appender. The explicit addition was removed; `source.ip` now reaches the log
  through MDC passthrough like every other field.
- `MockMvcTester`/`springSecurity()` do not reliably include arbitrary
  `FilterRegistrationBean`s the way a real servlet container does, unlike
  `HttpSecurity`-registered filters, which `springSecurity()` always simulates
  correctly. `MockMvcITSupport` now adds `LoggingContextCleanupFilter` and
  `RequestCorrelationContextFilter` to the builder explicitly rather than relying on
  Spring Boot's MockMvc auto-configuration to discover them.

## Consequences

`http.request.id`, `source.ip`, `client.ip`, `trace.id`, and `span.id` are now available
on every event this application logs, including `reject_request`; only `user.name`
remains conditional on authentication having run. A future filter that needs to run
ahead of Spring Security's chain must follow the same pattern: register it as a
`FilterRegistrationBean` at an order below Spring Security's own (and below
`LoggingContextCleanupFilter`'s `Ordered.HIGHEST_PRECEDENCE` if it adds MDC entries that
must survive to the end of the request), scope and remove only the MDC keys it adds
itself rather than blanket-clearing, and add it explicitly to `MockMvcITSupport` rather
than assuming MockMvc will discover it. Field contract and correlation semantics are
maintained in [`docs/system-design/05-crosscutting-concepts/logging/schema.md`](../system-design/05-crosscutting-concepts/logging/schema.md),
[`docs/system-design/05-crosscutting-concepts/logging/event-reference.md`](../system-design/05-crosscutting-concepts/logging/event-reference.md),
and [`docs/system-design/05-crosscutting-concepts/logging/README.md`](../system-design/05-crosscutting-concepts/logging/README.md).
