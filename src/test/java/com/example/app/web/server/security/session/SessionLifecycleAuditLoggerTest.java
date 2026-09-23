package com.example.app.web.server.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.MapSession;

class SessionLifecycleAuditLoggerTest {

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = new SessionLifecycleAuditLogger();

	private final Logger logger = (Logger) LoggerFactory.getLogger(SessionLifecycleAuditLogger.class);

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
	void usesASeparateRandomAuditIdentifierAndNeverTheServletSessionId() {
		MockHttpSession session = new MockHttpSession(null, "browser-session-credential");

		this.sessionLifecycleAuditLogger.logSessionCreatedIfNeeded(session);
		this.sessionLifecycleAuditLogger.logSessionDestroyed(session, "logout");

		assertThat(this.logEvents.list).hasSize(2);
		assertThat(this.logEvents.list).allSatisfy(event -> {
			assertThat(event.getFormattedMessage()).doesNotContain("browser-session-credential");
			assertThat(event.getKeyValuePairs().toString()).doesNotContain("browser-session-credential");
		});
		assertThat(this.logEvents.list.get(0).getKeyValuePairs().toString()).contains("session.id=");
		assertThat(this.logEvents.list.get(1).getKeyValuePairs().toString())
			.contains("session.termination_reason=\"logout\"");
	}

	@Test
	void logsDestroyedEventForASpringSessionUsingTheAuditIdentifier() {
		MapSession session = new MapSession("browser-session-credential");
		session.setAttribute(SessionLifecycleAuditLogger.AUDIT_SESSION_ID_ATTRIBUTE, "audit-id");

		this.sessionLifecycleAuditLogger.logSessionDestroyed(session, "privilege_change");

		assertThat(this.logEvents.list).singleElement().satisfies(event -> {
			assertThat(event.getFormattedMessage()).doesNotContain("browser-session-credential");
			assertThat(event.getKeyValuePairs().toString()).doesNotContain("browser-session-credential")
				.contains("session.id=\"audit-id\"")
				.contains("session.termination_reason=\"privilege_change\"");
		});
	}

	@Test
	void doesNotLogASpringSessionWithoutAnEstablishedAuditIdentifier() {
		MapSession session = new MapSession("browser-session-credential");

		this.sessionLifecycleAuditLogger.logSessionDestroyed(session, "privilege_change");

		assertThat(this.logEvents.list).isEmpty();
	}

	@Test
	void logsARequestedSessionThatWasNotFoundWithoutAnySessionIdentifier() {
		this.sessionLifecycleAuditLogger.logRequestedSessionNotFound();

		assertThat(this.logEvents.list).singleElement().satisfies(event -> {
			assertThat(event.getKeyValuePairs().toString()).contains("event.action=\"resume_session\"")
				.contains("event.outcome=\"failure\"")
				.contains("event.reason=\"session_not_found\"")
				.doesNotContain("session.id");
		});
	}

	@Test
	void logsAPrivilegeChangeWithTheAuditIdentifierUserAndChangedAuthorities() {
		MockHttpSession session = new MockHttpSession(null, "browser-session-credential");
		session.setAttribute(SessionLifecycleAuditLogger.AUDIT_SESSION_ID_ATTRIBUTE, "audit-id");

		this.sessionLifecycleAuditLogger.logSessionPrivilegeChanged(session, "alice",
				List.of("ROLE_USER_MANAGE", "ROLE_GROUP_MANAGE"), List.of("ROLE_APPLICATION_USER"));

		assertThat(this.logEvents.list).singleElement().satisfies(event -> {
			assertThat(event.getKeyValuePairs().toString()).doesNotContain("browser-session-credential")
				.contains("event.action=\"update_session\"")
				.contains("event.reason=\"privilege_change\"")
				.contains("session.id=\"audit-id\"")
				.contains("user.name=\"alice\"")
				.contains("session.authorities.added=\"[ROLE_GROUP_MANAGE, ROLE_USER_MANAGE]\"")
				.contains("session.authorities.removed=\"[ROLE_APPLICATION_USER]\"");
		});
	}

}
