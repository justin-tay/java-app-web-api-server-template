package com.example.app.web.server.security.session;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.example.app.web.server.test.OidcLoginITSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code SessionBindingFilter} through a real OIDC login, proving
 * the User-Agent baseline captured before login still governs the authenticated session.
 */
class SessionBindingIntegrationTest extends OidcLoginITSupport {

	@Test
	void retainsTheSessionWhenTheUserAgentIsUnchanged() throws Exception {
		String sessionCookie = login();

		HttpResponse<Void> response = this.client.send(HttpRequest.newBuilder(uri("/login-user"))
			.header(HttpHeaders.COOKIE, sessionCookie)
			.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.GET()
			.build(), HttpResponse.BodyHandlers.discarding());

		assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	void invalidatesTheSessionWhenTheUserAgentChanges() throws Exception {
		String sessionCookie = login();
		String sessionId = sessionId(sessionCookie);

		HttpResponse<Void> response = this.client.send(HttpRequest.newBuilder(uri("/login-user"))
			.header(HttpHeaders.COOKIE, sessionCookie)
			.header(HttpHeaders.USER_AGENT, "a-different-browser")
			.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.GET()
			.build(), HttpResponse.BodyHandlers.discarding());

		assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(sessionCount(sessionId)).isZero();
	}

}
