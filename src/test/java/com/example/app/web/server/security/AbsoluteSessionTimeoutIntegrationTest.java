package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Integration tests for absolute session expiration.
 */
@Import(AbsoluteSessionTimeoutIntegrationTest.FixedClockConfiguration.class)
class AbsoluteSessionTimeoutIntegrationTest extends RestTestClientITSupport {

	private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void expiredJdbcSessionIsRemovedBeforeProtectedRequestIsAuthorized() {
		String primaryId = UUID.randomUUID().toString();
		String sessionId = UUID.randomUUID().toString();
		this.jdbcTemplate.update(
				"INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME) VALUES (?, ?, ?, ?, ?, ?)",
				primaryId, sessionId, NOW.minus(Duration.ofHours(12)).toEpochMilli(), System.currentTimeMillis(), 3600,
				System.currentTimeMillis() + Duration.ofHours(1).toMillis());

		assertThat(RestTestClientResponse.from(this.restTestClient.get()
			.uri("/account")
			.header(HttpHeaders.COOKIE, "id=" + cookieValue(sessionId))
			.exchange())).hasStatus3xxRedirection();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRIMARY_ID = ?",
				Integer.class, primaryId))
			.isZero();
	}

	private String cookieValue(String sessionId) {
		return Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

}
