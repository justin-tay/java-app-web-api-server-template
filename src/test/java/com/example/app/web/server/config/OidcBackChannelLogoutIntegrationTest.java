package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.example.app.web.server.test.OidcLoginITSupport;

/**
 * Integration test for OIDC Back-Channel Logout configured by
 * {@link WebSecurityConfiguration}: a session established by a real OIDC login is ended
 * by a signed logout token sent to the application's back-channel endpoint.
 */
class OidcBackChannelLogoutIntegrationTest extends OidcLoginITSupport {

	@Test
	void backChannelLogoutDeletesTheSessionEstablishedByOidcLogin() throws Exception {
		String sessionCookie = login();
		String sessionId = sessionId(sessionCookie);
		assertThat(sessionCount(sessionId)).isEqualTo(1);
		assertThat(get("/login-user", sessionCookie).statusCode()).isEqualTo(HttpStatus.OK.value());

		HttpResponse<String> logoutResponse = this.client
			.send(HttpRequest.newBuilder(uri("/logout/connect/back-channel/keycloak"))
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
				.POST(HttpRequest.BodyPublishers.ofString("logout_token=" + logoutToken()))
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(logoutResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
		assertThat(sessionCount(sessionId)).isZero();
		assertThat(get("/login-user", sessionCookie).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
	}

	private HttpResponse<Void> get(String path, String sessionCookie) throws Exception {
		return this.client.send(HttpRequest.newBuilder(uri(path))
			.header(HttpHeaders.COOKIE, sessionCookie)
			.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.GET()
			.build(), HttpResponse.BodyHandlers.discarding());
	}

	private static String logoutToken() {
		return sign(new JOSEObjectType("logout+jwt"),
				new JWTClaimsSet.Builder().issuer(ISSUER)
					.subject(SUBJECT)
					.audience(CLIENT_ID)
					.issueTime(new Date())
					.jwtID(UUID.randomUUID().toString())
					.claim("sid", PROVIDER_SESSION_ID)
					.claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()))
					.build());
	}

}
