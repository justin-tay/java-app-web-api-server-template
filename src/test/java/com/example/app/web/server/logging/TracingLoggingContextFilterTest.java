package com.example.app.web.server.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.app.web.server.logging.client.ClientIpResolver;
import com.example.app.web.server.logging.request.RequestIdResolver;
import com.example.app.web.server.security.session.SessionLifecycleAuditLogger;

class TracingLoggingContextFilterTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void addsTraceAndSpanIdsToMdcWhenASpanIsActive() throws Exception {
		SimpleTracer tracer = new SimpleTracer();
		Span span = tracer.nextSpan().name("test").start();

		try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
			new TracingLoggingContextFilter(tracer).doFilter(new MockHttpServletRequest(),
					new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
						assertThat(MDC.get("trace.id")).isEqualTo(span.context().traceId());
						assertThat(MDC.get("span.id")).isEqualTo(span.context().spanId());
					});
		}

		assertThat(MDC.get("trace.id")).isNull();
		assertThat(MDC.get("span.id")).isNull();
	}

	@Test
	void addsNothingToMdcWhenNoSpanIsActive() throws Exception {
		SimpleTracer tracer = new SimpleTracer();

		new TracingLoggingContextFilter(tracer).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> {
					assertThat(MDC.get("trace.id")).isNull();
					assertThat(MDC.get("span.id")).isNull();
				});
	}

	@Test
	void traceAndSpanIdsSurviveBeingNestedInsideSecurityLoggingContextFilter() throws Exception {
		SimpleTracer tracer = new SimpleTracer();
		Span span = tracer.nextSpan().name("test").start();
		TracingLoggingContextFilter tracingLoggingContextFilter = new TracingLoggingContextFilter(tracer);
		SecurityLoggingContextFilter securityLoggingContextFilter = new SecurityLoggingContextFilter(
				ClientIpResolver.none(), RequestIdResolver.none(), new SessionLifecycleAuditLogger());

		try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
			securityLoggingContextFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
					(outerRequest, outerResponse) -> tracingLoggingContextFilter.doFilter(
							(MockHttpServletRequest) outerRequest, (MockHttpServletResponse) outerResponse,
							(innerRequest, innerResponse) -> {
								assertThat(MDC.get("trace.id")).isEqualTo(span.context().traceId());
								assertThat(MDC.get("span.id")).isEqualTo(span.context().spanId());
								assertThat(MDC.get("http.request.id")).isNotBlank();
							}));
		}

		assertThat(MDC.get("trace.id")).isNull();
		assertThat(MDC.get("span.id")).isNull();
	}

}
