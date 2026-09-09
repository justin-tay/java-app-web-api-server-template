package com.example.app.web.server.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.LoggerFactory;

import com.example.app.web.server.test.MockMvcITSupport;

/**
 * Tests that error responses use the Problem Details format (RFC 9457).
 *
 * <p>
 * Problem Details responses are produced by {@link ApiResponseEntityExceptionHandler}
 * which extends
 * {@link org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler}.
 * This means problem details are always active regardless of the
 * {@code spring.mvc.problemdetails.enabled} property, which only controls Spring Boot's
 * own {@code ProblemDetailsExceptionHandler} auto-configuration bean.
 */
@Import(ApiResponseEntityExceptionHandlerTest.RestClientResponseExceptionControllerConfiguration.class)
class ApiResponseEntityExceptionHandlerTest extends MockMvcITSupport {

	private final Logger logger = (Logger) LoggerFactory.getLogger(ApiResponseEntityExceptionHandler.class);

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
	@WithMockUser
	void notFoundReturnsProblemDetail() {
		assertThat(this.mockMvc.get().uri("/does-not-exist")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:route-not-found",
					  "status": 404,
					  "title": "Not Found"
					}
					""");
	}

	@Test
	@WithMockUser
	void methodNotAllowedReturnsProblemDetail() {
		assertThat(this.mockMvc.post().uri("/account").with(csrf())).hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:method-not-allowed",
					  "status": 405,
					  "title": "Method Not Allowed"
					}
					""");
	}

	@Test
	@WithMockUser
	void restClientResponseExceptionReturnsProblemDetail() {
		assertThat(this.mockMvc.get().uri("/test/rest-client-response-exception"))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:upstream-response-failed",
					  "status": 503,
					  "title": "Service Unavailable",
					  "detail": "The request could not be completed."
					}
					""");
		assertUnexpectedFailureLog(HttpStatus.SERVICE_UNAVAILABLE.value(), "/test/rest-client-response-exception",
				RestClientResponseException.class);
	}

	@Test
	@WithMockUser
	void unexpectedExceptionReturnsGenericProblemDetailAndLogsTheFailure() {
		assertThat(this.mockMvc.get().uri("/test/unexpected-exception")).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:internal-error",
					  "status": 500,
					  "title": "Internal Server Error",
					  "detail": "The request could not be completed."
					}
					""");
		assertUnexpectedFailureLog(HttpStatus.INTERNAL_SERVER_ERROR.value(), "/test/unexpected-exception",
				IllegalStateException.class);
	}

	private void assertUnexpectedFailureLog(int statusCode, String path, Class<? extends Exception> exceptionType) {
		assertThat(this.logEvents.list).singleElement().satisfies(event -> {
			Map<String, Object> keyValues = event.getKeyValuePairs()
				.stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
			assertThat(event.getLevel()).isEqualTo(Level.ERROR);
			assertThat(event.getThrowableProxy().getClassName()).isEqualTo(exceptionType.getName());
			assertThat(keyValues).containsEntry("event.category", List.of("web"))
				.containsEntry("event.type", List.of("error"))
				.containsEntry("event.action", "process_request")
				.containsEntry("event.outcome", "failure")
				.containsEntry("http.response.status_code", statusCode)
				.containsEntry("url.path", path)
				.containsEntry("error.type", exceptionType.getName());
			assertThat(event.getMDCPropertyMap()).containsKey("http.request.id");
		});
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RestClientResponseExceptionControllerConfiguration {

		@Bean
		RestClientResponseExceptionController restClientResponseExceptionController() {
			return new RestClientResponseExceptionController();
		}

	}

	@RestController
	static class RestClientResponseExceptionController {

		@GetMapping("/test/rest-client-response-exception")
		void throwRestClientResponseException() {
			throw new RestClientResponseException("Upstream service unavailable", HttpStatus.SERVICE_UNAVAILABLE,
					"Service Unavailable", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8);
		}

		@GetMapping("/test/unexpected-exception")
		void throwUnexpectedException() {
			throw new IllegalStateException("secret detail");
		}

	}

}
