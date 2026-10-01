package com.example.commons.security.oauth2;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityProviderUnavailableFilterTest {

	private static final String REDIRECT_URI = "/?error=identity_provider_unavailable";

	private final ClientRegistrationRepository unavailable = registrationId -> {
		throw new IdentityProviderUnavailableException(registrationId, Duration.ofSeconds(17));
	};

	@Test
	void answersServiceUnavailableWithRetryAfterAndAProblemDetailToAnApiClient() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new IdentityProviderUnavailableFilter(this.unavailable, REDIRECT_URI)
			.doFilter(request("/oauth2/authorization/keycloak", "application/json"), response, chain);

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getHeader("Retry-After")).isEqualTo("17");
		assertThat(response.getContentType()).startsWith("application/problem+json");
		assertThat(response.getContentAsString()).contains("urn:problem:identity-provider-unavailable")
			.contains("\"status\":503");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void redirectsABrowserNavigationToTheConfiguredUri() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new IdentityProviderUnavailableFilter(this.unavailable, REDIRECT_URI).doFilter(
				request("/oauth2/authorization/keycloak", "text/html,application/xhtml+xml"), response,
				new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getHeader("Location")).isEqualTo(REDIRECT_URI);
		assertThat(response.getHeader("Retry-After")).isEqualTo("17");
		assertThat(response.getContentAsString()).isEmpty();
	}

	@Test
	void answersServiceUnavailableToTheCallbackOfAnApiClient() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new IdentityProviderUnavailableFilter(this.unavailable, REDIRECT_URI)
			.doFilter(request("/login/oauth2/code/keycloak", "application/json"), response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(503);
	}

	@Test
	void roundsASubSecondRetryAfterUpToOneSecond() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		ClientRegistrationRepository almostReady = registrationId -> {
			throw new IdentityProviderUnavailableException(registrationId, Duration.ofMillis(200));
		};

		new IdentityProviderUnavailableFilter(almostReady, REDIRECT_URI)
			.doFilter(request("/oauth2/authorization/keycloak", "application/json"), response, new MockFilterChain());

		assertThat(response.getHeader("Retry-After")).isEqualTo("1");
	}

	@Test
	void doesNotResolveARegistrationForOtherRequests() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new IdentityProviderUnavailableFilter(this.unavailable, REDIRECT_URI).doFilter(request("/account", "text/html"),
				response, chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void passesALoginRequestThroughOnceTheRegistrationResolves() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new IdentityProviderUnavailableFilter(registrationId -> null, REDIRECT_URI)
			.doFilter(request("/oauth2/authorization/keycloak", "text/html"), response, chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	private static MockHttpServletRequest request(String path, String accept) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.addHeader("Accept", accept);
		return request;
	}

}
