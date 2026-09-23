package com.example.app.web.server.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Integration tests for requests that present a session ID the JDBC session repository
 * does not hold, such as a session past its idle timeout.
 */
class InvalidSessionIntegrationTest extends RestTestClientITSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void unknownSessionRequestedByAnApiIsLoggedAndReceivesUnauthorizedProblemDetail() {
		String sessionId = UUID.randomUUID().toString();
		String cookieValue = cookieValue(sessionId);

		this.restTestClient.get()
			.uri("/login-user")
			.header(HttpHeaders.COOKIE, "id=" + cookieValue)
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.UNAUTHORIZED)
			.expectBody(String.class)
			.isEqualTo("{\"type\":\"urn:problem:unauthenticated\",\"title\":\"Unauthorized\",\"status\":401}");

		assertThat(this.logEvents.list).filteredOn(event -> event.getKeyValuePairs() != null)
			.filteredOn(event -> event.getKeyValuePairs().toString().contains("event.action=\"resume_session\""))
			.singleElement()
			.satisfies(event -> assertThat(event.getKeyValuePairs().toString())
				.contains("event.reason=\"session_not_found\"")
				.doesNotContain(sessionId)
				.doesNotContain(cookieValue));
		assertThat(this.logEvents.list).allSatisfy(
				event -> assertThat(event.getFormattedMessage()).doesNotContain(sessionId).doesNotContain(cookieValue));
	}

	@Test
	void unknownSessionRequestedByABrowserIsLoggedAndRedirectedToLogin() {
		this.restTestClient.get()
			.uri("/login-user")
			.header(HttpHeaders.COOKIE, "id=" + cookieValue(UUID.randomUUID().toString()))
			.accept(MediaType.TEXT_HTML)
			.exchange()
			.expectStatus()
			.isFound()
			.expectHeader()
			.value(HttpHeaders.LOCATION, location -> assertThat(location).endsWith("/oauth2/authorization/keycloak"));

		assertThat(this.logEvents.list).anySatisfy(event -> assertThat(String.valueOf(event.getKeyValuePairs()))
			.contains("event.action=\"resume_session\""));
	}

	@Test
	void idleExpiredSessionIsLoggedWhenDetectedOnItsNextUse() {
		String primaryId = UUID.randomUUID().toString();
		String sessionId = UUID.randomUUID().toString();
		long lastAccessTime = System.currentTimeMillis() - Duration.ofMinutes(20).toMillis();
		this.jdbcTemplate.update(
				"INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME) VALUES (?, ?, ?, ?, ?, ?)",
				primaryId, sessionId, lastAccessTime, lastAccessTime, 900,
				lastAccessTime + Duration.ofMinutes(15).toMillis());

		this.restTestClient.get()
			.uri("/login-user")
			.header(HttpHeaders.COOKIE, "id=" + cookieValue(sessionId))
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isUnauthorized();

		assertThat(this.logEvents.list).anySatisfy(event -> assertThat(String.valueOf(event.getKeyValuePairs()))
			.contains("event.action=\"resume_session\"")
			.contains("event.reason=\"session_not_found\""));
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRIMARY_ID = ?",
				Integer.class, primaryId))
			.isZero();
	}

	@Test
	void stateChangingRequestWithAnUnknownSessionIsLoggedAndReceivesUnauthorizedProblemDetail() {
		this.restTestClient.post()
			.uri("/account")
			.header(HttpHeaders.COOKIE, "id=" + cookieValue(UUID.randomUUID().toString()))
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isUnauthorized();

		assertThat(this.logEvents.list).anySatisfy(event -> assertThat(String.valueOf(event.getKeyValuePairs()))
			.contains("event.action=\"resume_session\""));
	}

	@Test
	void requestWithoutASessionCookieIsNotLoggedAsAnUnknownSession() {
		this.restTestClient.get()
			.uri("/login-user")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isUnauthorized();

		assertThat(this.logEvents.list).noneSatisfy(event -> assertThat(String.valueOf(event.getKeyValuePairs()))
			.contains("event.action=\"resume_session\""));
	}

	private static String cookieValue(String sessionId) {
		return Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
	}

}
