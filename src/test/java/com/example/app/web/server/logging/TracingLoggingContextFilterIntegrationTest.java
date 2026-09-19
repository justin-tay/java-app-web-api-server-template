package com.example.app.web.server.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Confirms trace correlation is present on real request lifecycle log events, using the
 * full, real filter chain rather than the two application filters in isolation. This is
 * what actually proves {@code ServerHttpObservationFilter} starts the request's span
 * before {@code SecurityLoggingContextFilter} runs, since
 * {@link TracingLoggingContextFilter} depends on that ordering.
 */
class TracingLoggingContextFilterIntegrationTest extends RestTestClientITSupport {

	@Test
	void requestLifecycleEventsCarryTraceAndSpanIds() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			this.restTestClient.get().uri("/account").exchange();

			assertThat(appender.list).isNotEmpty();
			for (ILoggingEvent event : appender.list) {
				assertThat(event.getMDCPropertyMap()).containsKey("trace.id").containsKey("span.id");
				assertThat(event.getMDCPropertyMap().get("trace.id")).hasSize(32).matches("[0-9a-f]+");
				assertThat(event.getMDCPropertyMap().get("span.id")).hasSize(16).matches("[0-9a-f]+");
			}
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

}
