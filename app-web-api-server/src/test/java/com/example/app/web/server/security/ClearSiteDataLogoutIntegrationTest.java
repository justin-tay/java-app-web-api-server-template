package com.example.app.web.server.security;

import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the {@code Clear-Site-Data} header that
 * {@code commons.security.logout.clear-site-data} adds to a logout response.
 */
@SpringBootTest(properties = "commons.security.logout.clear-site-data=cookies,cache")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClearSiteDataLogoutIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void logoutOverHttpsClearsTheConfiguredDirectives() throws Exception {
		this.mockMvc
			.perform(post("/logout").secure(true).with(oidcLoginAs("admin")).with(csrf()).accept(MediaType.TEXT_HTML))
			.andExpect(header().string("Clear-Site-Data", "\"cookies\", \"cache\""));
	}

}
