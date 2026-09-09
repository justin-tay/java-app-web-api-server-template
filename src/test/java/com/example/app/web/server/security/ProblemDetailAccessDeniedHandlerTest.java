package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfException;

class ProblemDetailAccessDeniedHandlerTest {

	private final Logger logger = (Logger) LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

	private ListAppender<ILoggingEvent> logEvents;

	@BeforeEach
	void setUpAppender() {
		this.logEvents = new ListAppender<>();
		this.logEvents.start();
		this.logger.addAppender(this.logEvents);
	}

	@AfterEach
	void removeAppender() {
		this.logger.detachAppender(this.logEvents);
		this.logEvents.stop();
	}

	@Test
	void logsCsrfDenialWithEcsCategoriesAndKnownResponseStatus() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts");
		request.setRemoteAddr("192.0.2.10");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ProblemDetailAccessDeniedHandler().handle(request, response, new CsrfException("secret detail"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.getContentAsString()).contains("\"type\":\"urn:problem:csrf-validation-failed\"");
		Map<String, Object> keyValues = this.logEvents.list.get(0)
			.getKeyValuePairs()
			.stream()
			.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
		assertThat(keyValues).containsEntry("event.category", List.of("web", "api"))
			.containsEntry("event.type", List.of("access", "denied"))
			.containsEntry("event.action", "validate_csrf_token")
			.containsEntry("event.outcome", "failure")
			.containsEntry("http.response.status_code", HttpStatus.FORBIDDEN.value())
			.containsEntry("url.path", "/accounts")
			.containsEntry("source.ip", "192.0.2.10")
			.containsEntry("error.type", "CsrfException");
		assertThat(this.logEvents.list.get(0).getFormattedMessage()).doesNotContain("secret detail");
	}

}
