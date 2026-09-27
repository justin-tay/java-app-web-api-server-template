package com.example.commons.security.authentication.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class JsonAwareOidcLogoutSuccessHandlerTest {

	private static final String END_SESSION_ENDPOINT = "https://keycloak.example.com/realms/test/protocol/openid-connect/logout";

	private final JsonAwareOidcLogoutSuccessHandler handler = handler();

	@Test
	void aClientThatAsksForJsonGetsTheLogoutUrlInsteadOfARedirect() throws Exception {
		MockHttpServletRequest request = request("application/json");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.onLogoutSuccess(request, response, oidcLogin());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
		assertThat(response.getContentAsString()).startsWith("{\"logoutUrl\":\"" + END_SESSION_ENDPOINT + "?")
			.contains("id_token_hint=id-token")
			.contains("post_logout_redirect_uri=https://app.example.com/app/home");
	}

	@Test
	void aBrowserNavigationIsRedirectedToTheProvider() throws Exception {
		MockHttpServletRequest request = request("text/html,application/xhtml+xml,application/json;q=0.9");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.onLogoutSuccess(request, response, oidcLogin());

		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getHeader(HttpHeaders.LOCATION)).startsWith(END_SESSION_ENDPOINT + "?");
	}

	@Test
	void aLoginWithNoProviderSessionGetsTheApplicationsOwnUrl() throws Exception {
		MockHttpServletRequest request = request("application/json");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.handler.onLogoutSuccess(request, response, new TestingAuthenticationToken("alice", null, "ROLE_USER"));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString()).isEqualTo("{\"logoutUrl\":\"/\"}");
	}

	private static MockHttpServletRequest request(String accept) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
		request.setScheme("https");
		request.setServerName("app.example.com");
		request.setServerPort(443);
		request.addHeader(HttpHeaders.ACCEPT, accept);
		return request;
	}

	private static OAuth2AuthenticationToken oidcLogin() {
		OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject("alice").build();
		return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), idToken), List.of(), "keycloak");
	}

	private static JsonAwareOidcLogoutSuccessHandler handler() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
			.clientId("client")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.authorizationUri("https://keycloak.example.com/auth")
			.tokenUri("https://keycloak.example.com/token")
			.providerConfigurationMetadata(Map.of("end_session_endpoint", END_SESSION_ENDPOINT))
			.build();
		JsonAwareOidcLogoutSuccessHandler handler = new JsonAwareOidcLogoutSuccessHandler(
				new InMemoryClientRegistrationRepository(registration));
		handler.setPostLogoutRedirectUri("{baseUrl}/app/home");
		return handler;
	}

}
