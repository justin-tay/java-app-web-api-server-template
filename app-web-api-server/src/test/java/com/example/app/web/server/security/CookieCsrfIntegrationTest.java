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
 * Tests the cookie-to-header CSRF support a frontend uses: the token arrives in a cookie
 * JavaScript can read, and a state-changing request presents it in the
 * {@code X-XSRF-TOKEN} header.
 */
@SpringBootTest(
		properties = { "commons.security.passkeys.enabled=true", "commons.security.passkeys.relying-party.id=localhost",
				"commons.security.passkeys.allowed-origins=http://localhost:8081" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CookieCsrfIntegrationTest {

	private static final String STATE_CHANGING_PUBLIC_PATH = "/webauthn/authenticate/options";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void anyResponseCarriesATokenCookieThatJavaScriptCanRead() throws Exception {
		Cookie cookie = this.mockMvc.perform(get("/oauth2/jwks")).andReturn().getResponse().getCookie("XSRF-TOKEN");

		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
		assertThat(cookie.getPath()).isEqualTo("/");
	}

	@Test
	void aRequestEchoingTheCookieInTheHeaderIsAccepted() throws Exception {
		String token = token();

		this.mockMvc
			.perform(post(STATE_CHANGING_PUBLIC_PATH).cookie(new Cookie("XSRF-TOKEN", token))
				.header("X-XSRF-TOKEN", token))
			.andExpect(status().isOk());
	}

	@Test
	void aRequestWithoutTheHeaderOrWithAWrongOneIsRefused() throws Exception {
		String token = token();

		this.mockMvc.perform(post(STATE_CHANGING_PUBLIC_PATH).cookie(new Cookie("XSRF-TOKEN", token)))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post(STATE_CHANGING_PUBLIC_PATH).cookie(new Cookie("XSRF-TOKEN", token))
				.header("X-XSRF-TOKEN", "not-the-token"))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(post(STATE_CHANGING_PUBLIC_PATH).header("X-XSRF-TOKEN", token))
			.andExpect(status().isForbidden());
	}

	private String token() throws Exception {
		String setCookie = this.mockMvc.perform(get("/oauth2/jwks"))
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
			.findFirst()
			.orElseThrow();
		return setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));
	}

}
