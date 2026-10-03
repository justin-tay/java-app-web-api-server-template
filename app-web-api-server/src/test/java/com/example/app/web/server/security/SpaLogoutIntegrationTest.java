package com.example.app.web.server.security;

import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests logout as a single-page application uses it (see docs/adr/0025): a {@code fetch}
 * that asks for JSON is answered with the URL to send the browser to, and a browser
 * navigation is still redirected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaLogoutIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aClientThatAsksForJsonIsGivenTheLogoutUrl() throws Exception {
		this.mockMvc.perform(post("/logout").with(oidcLoginAs("admin")).with(csrf()).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Location"))
			.andExpect(jsonPath("$.logoutUrl").isNotEmpty());
	}

	@Test
	void logoutDoesNotClearSiteDataByDefault() throws Exception {
		this.mockMvc.perform(
				post("/logout").secure(true).with(oidcLoginAs("admin")).with(csrf()).accept(MediaType.APPLICATION_JSON))
			.andExpect(header().doesNotExist("Clear-Site-Data"));
	}

	@Test
	void aBrowserNavigationIsRedirected() throws Exception {
		this.mockMvc.perform(post("/logout").with(oidcLoginAs("admin")).with(csrf()).accept(MediaType.TEXT_HTML))
			.andExpect(status().is3xxRedirection());
	}

}
