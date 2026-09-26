package com.example.commons.security.authentication.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class MaxAgeAuthorizationRequestResolverTest {

	private final MaxAgeAuthorizationRequestResolver resolver = new MaxAgeAuthorizationRequestResolver(
			new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
				.clientId("app")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("openid")
				.authorizationUri("https://idp.example.test/auth")
				.tokenUri("https://idp.example.test/token")
				.build()));

	@Test
	void forwardsMaxAgeZeroToTheProvider() {
		OAuth2AuthorizationRequest authorizationRequest = this.resolver.resolve(loginRequest("0"));

		assertThat(authorizationRequest.getAdditionalParameters()).containsEntry("max_age", "0");
		assertThat(authorizationRequest.getAuthorizationRequestUri()).contains("max_age=0");
	}

	@Test
	void ignoresAnAbsentOrOtherMaxAge() {
		assertThat(this.resolver.resolve(loginRequest(null)).getAdditionalParameters()).doesNotContainKey("max_age");
		assertThat(this.resolver.resolve(loginRequest("3600")).getAdditionalParameters()).doesNotContainKey("max_age");
		assertThat(this.resolver.resolve(loginRequest("zero")).getAdditionalParameters()).doesNotContainKey("max_age");
	}

	@Test
	void resolvesNothingForOtherPaths() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
		request.setParameter("max_age", "0");

		assertThat(this.resolver.resolve(request)).isNull();
	}

	private static MockHttpServletRequest loginRequest(String maxAge) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
		request.setServletPath("/oauth2/authorization/keycloak");
		if (maxAge != null) {
			request.setParameter("max_age", maxAge);
		}
		return request;
	}

}
