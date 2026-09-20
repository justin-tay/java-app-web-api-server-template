package com.example.app.web.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Confirms {@link ProblemDetailErrorController} works end-to-end with the full
 * application: that it actually replaces Spring Boot's default
 * {@code BasicErrorController} for a real forwarded dispatch to {@code /error}, that its
 * {@code process_request} audit event is present in rendered log output with the
 * documented field values (not, for example, the literal string {@code "null"} for an
 * absent {@link RequestDispatcher} attribute), and that omitted attributes are omitted
 * from the log rather than defaulting to that literal string. This cannot be verified by
 * {@link ProblemDetailErrorControllerTest} alone, since that test instantiates the
 * controller directly rather than going through bean wiring or logging.
 *
 * <p>
 * A genuine dispatch outside every other handler is otherwise impractical to trigger from
 * a test, so a test-only controller reproduces the forward Tomcat/Spring MVC would
 * perform, using the same {@link RequestDispatcher} attributes.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(ProblemDetailErrorControllerIntegrationTest.ErrorDispatchTestConfiguration.class)
class ProblemDetailErrorControllerIntegrationTest extends RestTestClientITSupport {

	@Test
	void forwardedDispatchReceivesProblemDetailsAndLogsProcessRequest(CapturedOutput output) {
		assertThat(
				RestTestClientResponse.from(this.restTestClient.get().uri("/test/error-dispatch/trigger").exchange()))
			.hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:route-not-found",
					  "title": "Not Found",
					  "status": 404
					}
					""");

		String logLine = output.getAll()
			.lines()
			.filter((line) -> line.contains("\"action\":\"process_request\""))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No process_request event was logged"));
		assertThat(logLine).contains("\"category\":[\"web\"]");
		assertThat(logLine).contains("\"outcome\":\"failure\"");
		assertThat(logLine).contains("\"status_code\":404");
		assertThat(logLine).contains("\"path\":\"/original/failing/path\"");
		assertThat(logLine).contains("\"error\":{\"type\":\"java.lang.IllegalStateException\"}");
	}

	@Test
	void forwardWithNoErrorAttributesOmitsUrlPathAndErrorTypeRatherThanLoggingNull(CapturedOutput output) {
		assertThat(RestTestClientResponse
			.from(this.restTestClient.get().uri("/test/error-dispatch/trigger-without-attributes").exchange()))
			.hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:internal-error",
					  "title": "Internal Server Error",
					  "status": 500
					}
					""");

		String logLine = output.getAll()
			.lines()
			.filter((line) -> line.contains("\"action\":\"process_request\""))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No process_request event was logged"));
		assertThat(logLine).contains("\"status_code\":500");
		assertThat(logLine).doesNotContain("\"url\":{");
		assertThat(logLine).doesNotContain("\"error\":{");
	}

	@Test
	void forwardWithARealExceptionLogsARedactedStackTraceWithoutItsMessage(CapturedOutput output) {
		assertThat(RestTestClientResponse
			.from(this.restTestClient.get().uri("/test/error-dispatch/trigger-with-exception").exchange()))
			.hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.hasContentTypeCompatibleWith("application/problem+json");

		String logLine = output.getAll()
			.lines()
			.filter((line) -> line.contains("\"action\":\"process_request\""))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No process_request event was logged"));
		assertThat(logLine).doesNotContain(ErrorDispatchTriggerController.SENSITIVE_MESSAGE);
		assertThat(logLine).contains("\"stack_trace\"");
		assertThat(logLine).contains(IllegalStateException.class.getName());
		assertThat(logLine).contains(RuntimeException.class.getName());
		assertThat(logLine).contains(ErrorDispatchTriggerController.class.getName());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ErrorDispatchTestConfiguration {

		/**
		 * Also covers {@code /error} itself: Spring Security's filter chain re-runs on
		 * the {@code ERROR} dispatch (Boot's default {@code dispatcherTypes} for the
		 * security filter include {@code ERROR}, not just {@code REQUEST}), so the
		 * forward from the trigger controller below would otherwise fall through to the
		 * application's own chain and its {@code "/**".authenticated()} rule, which would
		 * deny it before {@link ProblemDetailErrorController} is ever reached.
		 */
		@Bean
		@Order(0)
		SecurityFilterChain errorDispatchTestSecurityFilterChain(HttpSecurity http) throws Exception {
			return http.securityMatcher("/test/error-dispatch/**", "/error")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
				.build();
		}

		@Bean
		ErrorDispatchTriggerController errorDispatchTriggerController() {
			return new ErrorDispatchTriggerController();
		}

	}

	/**
	 * Sets the same {@link RequestDispatcher} attributes, and performs the same forward
	 * to {@code /error}, that a real unhandled dispatch would arrive with, without
	 * needing to provoke a genuine one.
	 */
	@Controller
	static class ErrorDispatchTriggerController {

		static final String SENSITIVE_MESSAGE = "sensitive-request-content-should-not-be-logged";

		@GetMapping("/test/error-dispatch/trigger")
		void trigger(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
			request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/original/failing/path");
			request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, IllegalStateException.class);
			request.getRequestDispatcher("/error").forward(request, response);
		}

		@GetMapping("/test/error-dispatch/trigger-without-attributes")
		void triggerWithoutAttributes(HttpServletRequest request, HttpServletResponse response)
				throws ServletException, IOException {
			request.getRequestDispatcher("/error").forward(request, response);
		}

		@GetMapping("/test/error-dispatch/trigger-with-exception")
		void triggerWithException(HttpServletRequest request, HttpServletResponse response)
				throws ServletException, IOException {
			RuntimeException cause = new RuntimeException(SENSITIVE_MESSAGE);
			IllegalStateException exception = new IllegalStateException(SENSITIVE_MESSAGE, cause);
			request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
			request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, exception.getClass());
			request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
			request.getRequestDispatcher("/error").forward(request, response);
		}

	}

}
