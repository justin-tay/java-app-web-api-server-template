package com.example.commons.security.oauth2;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;

import static org.assertj.core.api.Assertions.assertThat;

class OidcDiscoveryHealthIndicatorTest {

	private static final String ISSUER = "https://issuer.example.test";

	private volatile boolean down = true;

	@Test
	void isDownUntilTheProviderHasBeenReachedThenStaysUp() {
		LazyClientRegistrationRepository repository = new LazyClientRegistrationRepository(properties(), issuerUri -> {
			if (this.down) {
				throw new DiscoveryException(false, "the provider is not reachable", null);
			}
			return Map.of("issuer", ISSUER, "authorization_endpoint", ISSUER + "/auth", "token_endpoint",
					ISSUER + "/token", "jwks_uri", ISSUER + "/certs", "response_types_supported",
					java.util.List.of("code"), "subject_types_supported", java.util.List.of("public"),
					"id_token_signing_alg_values_supported", java.util.List.of("RS256"));
		}, Duration.ofMillis(1), Clock.systemUTC());
		OidcDiscoveryHealthIndicator indicator = new OidcDiscoveryHealthIndicator(repository);

		Health down = indicator.health();
		assertThat(down.getStatus()).isEqualTo(Status.DOWN);
		assertThat(down.getDetails()).containsEntry("keycloak", "unresolved").containsKey("keycloakLastFailureAt");
		assertThat(down.getDetails().toString()).doesNotContain(ISSUER);

		this.down = false;
		sleepPastTheRetryInterval();

		// The readiness check itself retries, so no login request is needed.
		Health up = indicator.health();
		assertThat(up.getStatus()).isEqualTo(Status.UP);
		assertThat(up.getDetails()).containsEntry("keycloak", "resolved");
	}

	private static void sleepPastTheRetryInterval() {
		try {
			Thread.sleep(10);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static OAuth2ClientProperties properties() {
		OAuth2ClientProperties properties = new OAuth2ClientProperties();
		OAuth2ClientProperties.Registration registration = new OAuth2ClientProperties.Registration();
		registration.setClientId("client-id");
		registration.setAuthorizationGrantType("authorization_code");
		registration.setRedirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}");
		registration.setScope(Set.of("openid"));
		properties.getRegistration().put("keycloak", registration);
		OAuth2ClientProperties.Provider provider = new OAuth2ClientProperties.Provider();
		provider.setIssuerUri(ISSUER);
		properties.getProvider().put("keycloak", provider);
		return properties;
	}

}
