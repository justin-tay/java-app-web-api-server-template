package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests that the CSRF token cookie takes the {@code __Host-} prefix, and is always
 * {@code Secure}, when the session cookie does, as it does outside the plain-HTTP
 * profiles.
 */
@SpringBootTest(properties = { "server.servlet.session.cookie.name=__Host-id", "commons.security.passkeys.enabled=true",
		"commons.security.passkeys.relying-party.id=localhost",
		"commons.security.passkeys.allowed-origins=http://localhost:8081" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HostPrefixedCsrfCookieIntegrationTest {

	private static final String STATE_CHANGING_PUBLIC_PATH = "/webauthn/authenticate/options";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theTokenCookieHasTheHostPrefixAndIsSecureOverPlainHttp() throws Exception {
		Cookie cookie = this.mockMvc.perform(get("/oauth2/jwks"))
			.andReturn()
			.getResponse()
			.getCookie("__Host-XSRF-TOKEN");

		assertThat(cookie).isNotNull();
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getDomain()).isNull();
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
	}

	@Test
	void aRequestEchoingThePrefixedCookieInTheHeaderIsAccepted() throws Exception {
		String setCookie = this.mockMvc.perform(get("/oauth2/jwks"))
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(header -> header.startsWith("__Host-XSRF-TOKEN="))
			.findFirst()
			.orElseThrow();
		String token = setCookie.substring("__Host-XSRF-TOKEN=".length(), setCookie.indexOf(';'));

		this.mockMvc
			.perform(post(STATE_CHANGING_PUBLIC_PATH).cookie(new Cookie("__Host-XSRF-TOKEN", token))
				.header("X-XSRF-TOKEN", token))
			.andExpect(status().isOk());
	}

}
