package com.example.app.web.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests that the CSRF token cookie takes {@code Secure} from the session cookie's
 * {@code server.servlet.session.cookie.secure} when it is set, whatever the request
 * scheme is.
 */
@SpringBootTest(properties = "server.servlet.session.cookie.secure=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguredSecureCsrfCookieIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theTokenCookieIsSecureOverPlainHttpWhenTheSessionCookieSettingIsTrue() throws Exception {
		Cookie cookie = this.mockMvc.perform(get("/oauth2/jwks")).andReturn().getResponse().getCookie("XSRF-TOKEN");

		assertThat(cookie).isNotNull();
		assertThat(cookie.getSecure()).isTrue();
	}

}
