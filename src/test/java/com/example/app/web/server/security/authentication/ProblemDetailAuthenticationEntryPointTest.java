package com.example.app.web.server.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class ProblemDetailAuthenticationEntryPointTest {

	private final ProblemDetailAuthenticationEntryPoint entryPoint = new ProblemDetailAuthenticationEntryPoint(
			"/oauth2/authorization/keycloak");

	@Test
	void writesProblemDetailForNonBrowserClient() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
		request.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.entryPoint.commence(request, response, new BadCredentialsException("no credentials"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
		assertThat(response.getContentAsString())
			.isEqualTo("{\"type\":\"urn:problem:unauthenticated\",\"title\":\"Unauthorized\",\"status\":401}");
	}

	@Test
	void redirectsToAuthorizationEndpointForBrowserClient() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
		request.addHeader("Accept", MediaType.TEXT_HTML_VALUE);
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.entryPoint.commence(request, response, new BadCredentialsException("no credentials"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/keycloak");
		assertThat(response.getContentAsString()).isEmpty();
	}

}
