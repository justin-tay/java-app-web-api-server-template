package com.example.app.web.server.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionLifecycleAuditInitializationFilterTest {

	@Test
	void triggersSessionAuditIdInitializationWhenASessionExistsAfterProcessing() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(SessionLifecycleAuditLogger.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
			request.getSession(true);

			new SessionLifecycleAuditInitializationFilter(new SessionLifecycleAuditLogger()).doFilter(request,
					new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
					});

			assertThat(appender.list).anySatisfy(event -> assertThat(event.getKeyValuePairs())
				.anySatisfy(pair -> assertThat(pair.value).isEqualTo("create_session")));
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void doesNothingWhenNoSessionExists() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(SessionLifecycleAuditLogger.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			new SessionLifecycleAuditInitializationFilter(new SessionLifecycleAuditLogger()).doFilter(
					new MockHttpServletRequest("GET", "/accounts"), new MockHttpServletResponse(),
					(servletRequest, servletResponse) -> {
					});

			assertThat(appender.list).isEmpty();
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

}
