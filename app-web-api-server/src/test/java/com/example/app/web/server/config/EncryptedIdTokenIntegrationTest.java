package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.example.app.web.server.test.OidcLoginITSupport;

/**
 * Integration test for ID token encryption (see docs/adr/0020): the test JWKS has an
 * {@code enc} key, so the application decrypts ID tokens encrypted to it and rejects ID
 * tokens that are not encrypted.
 */
class EncryptedIdTokenIntegrationTest extends OidcLoginITSupport {

	@Test
	void logsInWithAnEncryptedIdToken() throws Exception {
		String sessionCookie = login();

		assertThat(sessionCount(sessionId(sessionCookie))).isEqualTo(1);
	}

	@Test
	void rejectsAnIdTokenThatIsNotEncrypted() throws Exception {
		encryptIdTokensTo(null);

		HttpResponse<Void> callbackResponse = authorizationCodeFlow();

		assertThat(callbackResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(callbackResponse.headers().firstValue(HttpHeaders.LOCATION))
			.hasValueSatisfying(location -> assertThat(location).contains("error"));
	}

}
