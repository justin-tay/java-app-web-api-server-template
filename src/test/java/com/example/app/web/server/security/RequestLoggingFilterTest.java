package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void logsRequestLifecycleUsingSanitizedUrlAndAuthenticatedUser() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts/123");
			request.setQueryString("page=2&code=secret");
			request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/accounts/{id}");
			MockHttpServletResponse response = new MockHttpServletResponse();
			response.setStatus(200);
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("alice", "N/A", null));

			new RequestLoggingFilter(List.of("code")).doFilter(request, response, (servletRequest, servletResponse) -> {
			});

			assertThat(appender.list).hasSize(2);
			assertThat(keyValues(appender.list.get(0))).containsEntry("event.action", "receive_request")
				.containsEntry("url.path", "/accounts/123")
				.containsEntry("url.query", "page=2&code=[REDACTED]")
				.containsEntry("url.query_keys", List.of("page", "code"));
			Map<String, Object> completedEvent = keyValues(appender.list.get(1));
			assertThat(completedEvent).containsEntry("event.action", "complete_request")
				.containsEntry("http.response.status_code", 200)
				.containsEntry("http.route", "/accounts/{id}")
				.containsEntry("user.name", "alice")
				.containsKey("event.duration");
			assertThat(completedEvent.get("event.duration")).isEqualTo(Duration
				.between((Instant) completedEvent.get("event.start"), (Instant) completedEvent.get("event.end"))
				.toNanos());
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	private Map<String, Object> keyValues(ILoggingEvent event) {
		return event.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

}
