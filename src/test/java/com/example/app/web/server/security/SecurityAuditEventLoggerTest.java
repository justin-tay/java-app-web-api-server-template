package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;

/**
 * Tests the security audit events written to the structured logger.
 */
class SecurityAuditEventLoggerTest {

	private final SecurityAuditEventLogger securityAuditEventLogger = new SecurityAuditEventLogger();

	private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityAuditEventLogger.class);

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
	void logsAuthenticationSuccess() {
		Authentication authentication = authentication("alice");

		this.securityAuditEventLogger
			.onAuthenticationSuccess(new InteractiveAuthenticationSuccessEvent(authentication, getClass()));

		assertLog(Level.INFO, "User authenticated", new KeyValuePair("event.category", "authentication"),
				new KeyValuePair("event.action", "login"), new KeyValuePair("event.outcome", "success"),
				new KeyValuePair("user.name", "alice"));
	}

	@Test
	void logsAuthenticationFailureWithoutTheExceptionMessage() {
		Authentication authentication = authentication("alice");

		this.securityAuditEventLogger.onAuthenticationFailure(new AuthenticationFailureBadCredentialsEvent(
				authentication, new BadCredentialsException("secret detail")));

		assertLog(Level.WARN, "Authentication failed", new KeyValuePair("event.category", "authentication"),
				new KeyValuePair("event.action", "login"), new KeyValuePair("event.outcome", "failure"),
				new KeyValuePair("user.name", "alice"), new KeyValuePair("error.type", "BadCredentialsException"));
		assertThat(this.logEvents.list.get(0).getFormattedMessage()).doesNotContain("secret detail");
	}

	@Test
	void logsAuthorizationDenialForAnAnonymousUser() {
		Supplier<Authentication> authentication = () -> null;

		this.securityAuditEventLogger.onAuthorizationDenied(
				new AuthorizationDeniedEvent<>(authentication, new Object(), new AuthorizationDecision(false)));

		assertLog(Level.WARN, "Authorization denied", new KeyValuePair("event.category", List.of("web", "api")),
				new KeyValuePair("event.type", List.of("access", "denied")),
				new KeyValuePair("event.action", "authorize_access"), new KeyValuePair("event.outcome", "failure"),
				new KeyValuePair("user.name", "anonymous"));
	}

	@Test
	void logsLogoutSuccess() {
		this.securityAuditEventLogger.onLogoutSuccess(new LogoutSuccessEvent(authentication("alice")));

		assertLog(Level.INFO, "User logged out", new KeyValuePair("event.category", "authentication"),
				new KeyValuePair("event.action", "logout"), new KeyValuePair("event.outcome", "success"),
				new KeyValuePair("user.name", "alice"));
	}

	private Authentication authentication(String username) {
		return new TestingAuthenticationToken(username, "not-a-secret");
	}

	private void assertLog(Level level, String message, KeyValuePair... keyValuePairs) {
		assertThat(this.logEvents.list).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(level);
			assertThat(event.getFormattedMessage()).isEqualTo(message);
			assertThat(event.getKeyValuePairs()).contains(keyValuePairs);
		});
	}

}
