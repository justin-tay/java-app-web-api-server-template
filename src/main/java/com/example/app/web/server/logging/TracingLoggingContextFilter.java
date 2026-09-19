package com.example.app.web.server.logging;

import java.io.IOException;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds ECS trace correlation fields to the logging context.
 * <p>
 * Micrometer Tracing already populates its own {@code traceId}/{@code spanId} MDC keys,
 * but Spring Boot's ECS structured log formatter passes MDC entries through verbatim, so
 * those keys are not renamed to ECS's {@code trace.id}/{@code span.id}. This filter reads
 * the active span from {@link Tracer} directly and adds it under ECS's field names,
 * rather than depending on undocumented behavior of when Micrometer's own MDC keys are
 * (re)established relative to other filters that clear or rewrite MDC in this chain.
 */
@Component
public class TracingLoggingContextFilter extends OncePerRequestFilter {

	private static final String TRACE_ID = "trace.id";

	private static final String SPAN_ID = "span.id";

	private final Tracer tracer;

	public TracingLoggingContextFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Span currentSpan = this.tracer.currentSpan();
		if (currentSpan == null) {
			filterChain.doFilter(request, response);
			return;
		}
		try (MDC.MDCCloseable traceId = MDC.putCloseable(TRACE_ID, currentSpan.context().traceId());
				MDC.MDCCloseable spanId = MDC.putCloseable(SPAN_ID, currentSpan.context().spanId())) {
			filterChain.doFilter(request, response);
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

}
