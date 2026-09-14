package com.example.app.web.server.security.firewall;

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
import org.springframework.security.web.firewall.RequestRejectedException;

class ProblemDetailRequestRejectedHandlerTest {

	private final Logger logger = (Logger) LoggerFactory.getLogger(ProblemDetailRequestRejectedHandler.class);

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
	void logsRejectionWithEcsCategoriesAndWritesProblemDetail() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users/../secrets");
		request.setRemoteAddr("192.0.2.10");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ProblemDetailRequestRejectedHandler().handle(request, response,
				new RequestRejectedException("The request was rejected because the URL was not normalized."));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
		assertThat(response.getContentAsString())
			.isEqualTo("{\"type\":\"urn:problem:request-rejected\",\"title\":\"Bad Request\",\"status\":400}");

		Map<String, Object> keyValues = this.logEvents.list.get(0)
			.getKeyValuePairs()
			.stream()
			.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
		assertThat(keyValues).containsEntry("event.category", List.of("web"))
			.containsEntry("event.type", List.of("error"))
			.containsEntry("event.action", "reject_request")
			.containsEntry("event.outcome", "failure")
			.containsEntry("http.response.status_code", HttpStatus.BAD_REQUEST.value())
			.containsEntry("http.request.method", "GET")
			.containsEntry("url.path", "/admin/users/../secrets")
			.containsEntry("source.ip", "192.0.2.10")
			.containsEntry("error.type", "RequestRejectedException");
		assertThat(this.logEvents.list.get(0).getFormattedMessage())
			.doesNotContain("The request was rejected because the URL was not normalized.");
	}

	@Test
	void doesNotWriteResponseWhenAlreadyCommitted() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setCommitted(true);

		new ProblemDetailRequestRejectedHandler().handle(request, response, new RequestRejectedException("rejected"));

		assertThat(response.getContentAsString()).isEmpty();
	}

}
