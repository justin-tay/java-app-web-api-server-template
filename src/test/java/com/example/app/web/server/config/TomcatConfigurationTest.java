package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Tests the Tomcat configuration.
 */
class TomcatConfigurationTest extends RestTestClientITSupport {

	/**
	 * Verifies that malformed HTTP input rejected before Spring MVC receives a generic
	 * Problem Details response instead of Tomcat's default implementation-revealing HTML
	 * error page.
	 */
	@Test
	void oversizedRequestHeaderDoesNotRevealTomcatHtmlErrorPage() {
		RestTestClientResponse response = RestTestClientResponse.from(this.restTestClient.get()
			.uri("/oauth2/jwks")
			.header("X-Oversized-Header", "a".repeat(64 * 1024))
			.exchange());

		assertThat(response).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "title": "Bad Request",
					  "status": 400
					}
					""");
	}

}
