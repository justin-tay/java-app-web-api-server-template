package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.assertj.RestTestClientResponse;

import com.example.app.web.server.test.RestTestClientITSupport;

/**
 * Tests that the commons security baseline applies to this application's filter chain.
 */
public class WebSecurityConfigurationTest extends RestTestClientITSupport {

	@LocalServerPort
	private int port;

	/**
	 * The application's own {@code keycloak} registration (from {@code application.yaml})
	 * sends PKCE with {@code S256}, which Spring Security applies by default to an
	 * authorization code client, and the exact redirect URI that
	 * {@code bin/configure-keycloak.js} registers. Keycloak's client requires both.
	 */
	@Test
	void authorizationRequestSendsS256PkceAndTheExactRegisteredRedirectUri() throws Exception {
		HttpResponse<Void> response = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.build()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/oauth2/authorization/keycloak"))
				.GET()
				.build(), HttpResponse.BodyHandlers.discarding());

		assertThat(response.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		Map<String, String> parameters = Arrays.stream(
				URI.create(response.headers().firstValue(HttpHeaders.LOCATION).orElseThrow()).getRawQuery().split("&"))
			.map(parameter -> parameter.split("=", 2))
			.collect(Collectors.toMap(parameter -> URLDecoder.decode(parameter[0], StandardCharsets.UTF_8),
					parameter -> URLDecoder.decode(parameter[1], StandardCharsets.UTF_8)));
		assertThat(parameters).containsEntry("response_type", "code")
			.containsEntry("code_challenge_method", "S256")
			.containsEntry("redirect_uri", "http://localhost:" + this.port + "/login/oauth2/code/keycloak");
		// A base64url-encoded SHA-256 digest is 43 characters.
		assertThat(parameters.get("code_challenge")).matches("[A-Za-z0-9_-]{43}");
	}

	@Test
	void unauthenticatedRequestCreatesIdSessionCookie() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get()
			.uri("/account")
			.header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
			.exchange())).hasStatus3xxRedirection().cookies().containsCookie("id").doesNotContainCookie("JSESSIONID");
	}

	@Test
	void idSessionCookieIsNonPersistentHttpOnlyAndSameSiteLax() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get()
			.uri("/account")
			.header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
			.exchange())).hasStatus3xxRedirection().cookies().hasCookieSatisfying("id", cookie -> {
				assertThat(cookie.getMaxAge()).isEqualTo(-1);
				assertThat(cookie.isHttpOnly()).isTrue();
				assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
			});
	}

	@Test
	void unauthenticatedApiRequestReturnsProblemDetail() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get()
			.uri("/account")
			.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.exchange())).hasStatus(HttpStatus.UNAUTHORIZED)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:unauthenticated",
					  "title": "Unauthorized",
					  "status": 401
					}
					""");
	}

	@Test
	void csrfFailureReturnsActionableProblemDetail() {
		assertThat(RestTestClientResponse.from(this.restTestClient.post().uri("/account").exchange()))
			.hasStatus(HttpStatus.FORBIDDEN)
			.hasContentTypeCompatibleWith("application/problem+json")
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "type": "urn:problem:csrf-validation-failed",
					  "title": "Forbidden",
					  "status": 403,
					  "detail": "The request could not be verified. Refresh the page and try again."
					}
					""");
	}

	@Test
	void responseHasContentSecurityPolicy() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/oauth2/jwks").exchange())).hasStatusOk()
			.hasHeader("Content-Security-Policy",
					"base-uri 'none';default-src 'none';form-action 'none';frame-ancestors 'none'");
	}

	@Test
	void responseHasReferrerAndPermissionsPolicies() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/oauth2/jwks").exchange())).hasStatusOk()
			.hasHeader("Referrer-Policy", "strict-origin-when-cross-origin")
			.hasHeader("Permissions-Policy", "camera=(), geolocation=(), microphone=(), payment=(), usb=()");
	}

	@Test
	void responseHasSpringSecurityDefaultHeaders() {
		assertThat(RestTestClientResponse.from(this.restTestClient.get().uri("/oauth2/jwks").exchange())).hasStatusOk()
			.hasHeader("X-Frame-Options", "DENY")
			.hasHeader("X-XSS-Protection", "0")
			.hasHeader("X-Content-Type-Options", "nosniff")
			.hasHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
			.hasHeader("Pragma", "no-cache")
			.hasHeader("Expires", "0")
			.doesNotContainHeader("X-Powered-By");
	}

	@Test
	void rejectsDoublyEncodedPathInsteadOfDecodingTwice() {
		// If the path were decoded twice, "%252e%252e%252f" would resolve to "../" and
		// could
		// escape the "/admin/users/" prefix. Instead, Spring Security's firewall rejects
		// the
		// doubly-encoded sequence outright, so it is never decoded a second time.
		assertThat(RestTestClientResponse
			.from(this.restTestClient.get().uri("/admin/users/%252e%252e%252fadmin").exchange()))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

}
