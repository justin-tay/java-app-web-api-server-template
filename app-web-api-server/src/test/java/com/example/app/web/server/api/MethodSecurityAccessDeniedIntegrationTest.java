package com.example.app.web.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.app.web.server.test.MockMvcITSupport;
import com.example.commons.web.problem.ApiResponseEntityExceptionHandler;

/**
 * Tests that a {@code @PreAuthorize} denial inside a controller, for a user the URL
 * authorization rules already admitted, is answered as an authorization failure rather
 * than as an unexpected error.
 */
@Import(MethodSecurityAccessDeniedIntegrationTest.MethodSecuredControllerConfiguration.class)
class MethodSecurityAccessDeniedIntegrationTest extends MockMvcITSupport {

	private final Logger auditLogger = (Logger) LoggerFactory
		.getLogger("com.example.commons.security.SecurityAuditEventLogger");

	private final Logger exceptionHandlerLogger = (Logger) LoggerFactory
		.getLogger(ApiResponseEntityExceptionHandler.class);

	private ListAppender<ILoggingEvent> auditEvents;

	private ListAppender<ILoggingEvent> exceptionHandlerEvents;

	@BeforeEach
	void setUpAppenders() {
		this.auditEvents = new ListAppender<>();
		this.auditEvents.start();
		this.auditLogger.addAppender(this.auditEvents);
		this.exceptionHandlerEvents = new ListAppender<>();
		this.exceptionHandlerEvents.start();
		this.exceptionHandlerLogger.addAppender(this.exceptionHandlerEvents);
	}

	@AfterEach
	void removeAppenders() {
		this.auditLogger.detachAppender(this.auditEvents);
		this.auditEvents.stop();
		this.exceptionHandlerLogger.detachAppender(this.exceptionHandlerEvents);
		this.exceptionHandlerEvents.stop();
	}

	@Test
	void methodSecurityDenialReturnsAccessDeniedProblemDetailAndIsAudited() {
		assertThat(this.mockMvc.get()
			.uri("/test/method-security/denied")
			.with(user("test-user").roles("APPLICATION_USER"))
			.accept(MediaType.APPLICATION_JSON)).hasStatus(HttpStatus.FORBIDDEN)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.isStrictlyEqualTo("""
					{"type":"urn:problem:access-denied","title":"Forbidden","status":403}
					""");

		assertThat(this.auditEvents.list).singleElement().satisfies(event -> {
			Map<String, Object> keyValues = event.getKeyValuePairs()
				.stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
			assertThat(event.getLevel()).isEqualTo(Level.WARN);
			assertThat(keyValues).containsEntry("event.category", List.of("web", "api"))
				.containsEntry("event.type", List.of("access", "denied"))
				.containsEntry("event.action", "authorize_access")
				.containsEntry("event.outcome", "failure");
		});
		assertThat(this.exceptionHandlerEvents.list).isEmpty();
	}

	@Test
	void methodSecurityAllowsAUserWithTheRequiredRole() {
		assertThat(this.mockMvc.get()
			.uri("/test/method-security/denied")
			.with(user("admin").roles("APPLICATION_USER", "METHOD_TEST"))
			.accept(MediaType.APPLICATION_JSON)).hasStatusOk();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MethodSecuredControllerConfiguration {

		@Bean
		MethodSecuredController methodSecuredController() {
			return new MethodSecuredController();
		}

	}

	@RestController
	static class MethodSecuredController {

		/**
		 * Reachable by any authenticated user under the URL rules ({@code /**}
		 * authenticated), but restricted further by method security.
		 */
		@GetMapping("/test/method-security/denied")
		@PreAuthorize("hasRole('METHOD_TEST')")
		String denied() {
			return "allowed";
		}

	}

}
