package com.example.commons.security.oauth2;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityProviderUnavailableFilterTest {

	private final ClientRegistrationRepository unavailable = registrationId -> {
		throw new IdentityProviderUnavailableException(registrationId, Duration.ofSeconds(17));
	};

	@Test
	void answersServiceUnavailableWithRetryAfterAndAProblemDetailToAnAuthorizationRequest() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new IdentityProviderUnavailableFilter(this.unavailable)
			.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak"), response, chain);

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getHeader("Retry-After")).isEqualTo("17");
		assertThat(response.getContentType()).startsWith("application/problem+json");
		assertThat(response.getContentAsString()).contains("urn:problem:identity-provider-unavailable")
			.contains("\"status\":503");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void answersServiceUnavailableToTheCallback() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new IdentityProviderUnavailableFilter(this.unavailable).doFilter(
				new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak"), response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(503);
	}

	@Test
	void roundsASubSecondRetryAfterUpToOneSecond() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		ClientRegistrationRepository almostReady = registrationId -> {
			throw new IdentityProviderUnavailableException(registrationId, Duration.ofMillis(200));
		};

		new IdentityProviderUnavailableFilter(almostReady).doFilter(
				new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak"), response, new MockFilterChain());

		assertThat(response.getHeader("Retry-After")).isEqualTo("1");
	}

	@Test
	void doesNotResolveARegistrationForOtherRequests() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new IdentityProviderUnavailableFilter(this.unavailable).doFilter(new MockHttpServletRequest("GET", "/account"),
				response, chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void passesALoginRequestThroughOnceTheRegistrationResolves() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new IdentityProviderUnavailableFilter(registrationId -> null)
			.doFilter(new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak"), response, chain);

		assertThat(chain.getRequest()).isNotNull();
	}

}
