package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.example.app.web.server.test.OidcLoginITSupport;

/**
 * Tests that logout expires the session and CSRF cookies in the browser, over a session
 * established by a real OIDC login, so a stale session ID is not sent with the next
 * request.
 */
class LogoutCookiesIntegrationTest extends OidcLoginITSupport {

	@Test
	void logoutExpiresTheSessionAndCsrfCookies() throws Exception {
		String sessionCookie = login();
		HttpResponse<Void> userResponse = this.client.send(HttpRequest.newBuilder(uri("/login-user"))
			.header(HttpHeaders.COOKIE, sessionCookie)
			.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.GET()
			.build(), HttpResponse.BodyHandlers.discarding());
		assertThat(userResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
		String csrfCookie = cookie(userResponse, "XSRF-TOKEN");
		assertThat(csrfCookie).as("the CSRF cookie issued to the logged-in session").isNotNull();

		HttpResponse<Void> logoutResponse = this.client.send(HttpRequest.newBuilder(uri("/logout"))
			.header(HttpHeaders.COOKIE, sessionCookie + "; " + csrfCookie)
			.header("X-XSRF-TOKEN", csrfCookie.substring("XSRF-TOKEN=".length()))
			.header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
			.POST(HttpRequest.BodyPublishers.noBody())
			.build(), HttpResponse.BodyHandlers.discarding());

		assertThat(logoutResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		assertExpired(logoutResponse, "id");
		assertExpired(logoutResponse, "XSRF-TOKEN");
	}

	/**
	 * Asserts the response clears the cookie, which a browser does for an empty value
	 * with either {@code Max-Age=0} or an {@code Expires} in the past.
	 */
	private static void assertExpired(HttpResponse<?> response, String name) {
		assertThat(setCookies(response, name)).as("%s cookie", name)
			.singleElement()
			.satisfies(cookie -> assertThat(cookie).startsWith(name + "=;")
				.matches(".*(Max-Age=0|Expires=Thu, 01 Jan 1970).*"));
	}

	private static String cookie(HttpResponse<?> response, String name) {
		return setCookies(response, name).stream()
			.findFirst()
			.map(cookie -> cookie.substring(0, cookie.indexOf(';')))
			.orElse(null);
	}

	private static List<String> setCookies(HttpResponse<?> response, String name) {
		return response.headers()
			.allValues(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(c -> c.startsWith(name + "="))
			.toList();
	}

}
