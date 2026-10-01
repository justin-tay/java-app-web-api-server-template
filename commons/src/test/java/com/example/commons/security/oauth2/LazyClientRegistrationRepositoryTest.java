package com.example.commons.security.oauth2;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class LazyClientRegistrationRepositoryTest {

	private static final String ISSUER = "https://issuer.example.test/realms/test";

	private static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

	private final MutableClock clock = new MutableClock();

	private final AtomicInteger fetches = new AtomicInteger();

	private volatile DiscoveryException failure;

	private LazyClientRegistrationRepository repository() {
		return repository(properties(null));
	}

	private LazyClientRegistrationRepository repository(OAuth2ClientProperties properties) {
		return new LazyClientRegistrationRepository(properties, issuerUri -> {
			this.fetches.incrementAndGet();
			if (this.failure != null) {
				throw this.failure;
			}
			return metadata();
		}, RETRY_INTERVAL, this.clock);
	}

	@Test
	void knowsTheRegistrationIdsWithoutFetching() {
		LazyClientRegistrationRepository repository = repository();

		assertThat(repository.registrationIds()).containsExactly("keycloak");
		assertThat(this.fetches).hasValue(0);
	}

	@Test
	void startsWhileTheProviderIsDown() {
		this.failure = new DiscoveryException(false, "the provider is not reachable", null);
		LazyClientRegistrationRepository repository = repository();

		repository.resolveAtStartup();

		assertThat(repository.states().get("keycloak").resolved()).isFalse();
		assertThat(repository.states().get("keycloak").lastFailureAt()).isEqualTo(this.clock.instant());
	}

	@Test
	void failsStartupWhenTheMetadataIsDefinitivelyInvalid() {
		this.failure = new DiscoveryException(true, "HTTP 404", null);

		assertThatIllegalStateException().isThrownBy(() -> repository().resolveAtStartup())
			.withMessageContaining("keycloak")
			.withMessageContaining("HTTP 404");
	}

	@Test
	void resolvesOnFirstUseAndKeepsTheRegistrationForever() {
		LazyClientRegistrationRepository repository = repository();

		ClientRegistration registration = repository.findByRegistrationId("keycloak");
		this.clock.advance(Duration.ofDays(30));

		assertThat(repository.findByRegistrationId("keycloak")).isSameAs(registration);
		assertThat(this.fetches).hasValue(1);
		assertThat(repository.states().get("keycloak").resolved()).isTrue();
	}

	@Test
	void doesNotCallTheProviderAgainUntilTheRetryIntervalHasPassed() {
		this.failure = new DiscoveryException(false, "the provider is not reachable", null);
		LazyClientRegistrationRepository repository = repository();

		assertThatExceptionOfType(IdentityProviderUnavailableException.class)
			.isThrownBy(() -> repository.findByRegistrationId("keycloak"))
			.satisfies(ex -> assertThat(ex.getRetryAfter()).isEqualTo(RETRY_INTERVAL));
		this.clock.advance(Duration.ofSeconds(10));
		assertThatExceptionOfType(IdentityProviderUnavailableException.class)
			.isThrownBy(() -> repository.findByRegistrationId("keycloak"))
			.satisfies(ex -> assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(20)));
		assertThat(this.fetches).hasValue(1);

		this.clock.advance(Duration.ofSeconds(20));
		this.failure = null;

		assertThat(repository.findByRegistrationId("keycloak")).isNotNull();
		assertThat(this.fetches).hasValue(2);
	}

	@Test
	void buildsTheRegistrationFromThePropertiesAndTheDiscoveredMetadata() {
		ClientRegistration registration = repository().findByRegistrationId("keycloak");

		assertThat(registration.getClientId()).isEqualTo("client-id");
		assertThat(registration.getClientAuthenticationMethod().getValue()).isEqualTo("private_key_jwt");
		assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(ISSUER);
		assertThat(registration.getProviderDetails().getAuthorizationUri()).isEqualTo(ISSUER + "/auth");
		assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(ISSUER + "/token");
		assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo(ISSUER + "/certs");
		assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
			.isEqualTo("preferred_username");
		assertThat(registration.getProviderDetails().getConfigurationMetadata()).containsEntry("end_session_endpoint",
				ISSUER + "/logout");
	}

	@Test
	void prefersAnEndpointTheConfigurationSets() {
		ClientRegistration registration = repository(properties(ISSUER + "/custom-token"))
			.findByRegistrationId("keycloak");

		assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(ISSUER + "/custom-token");
		assertThat(registration.getProviderDetails().getAuthorizationUri()).isEqualTo(ISSUER + "/auth");
	}

	@Test
	void buildsARegistrationWithoutAnIssuerUriAtOnce() {
		OAuth2ClientProperties properties = properties(null);
		properties.getProvider().get("keycloak").setIssuerUri(null);
		properties.getProvider().get("keycloak").setAuthorizationUri("https://static.example.test/auth");
		properties.getProvider().get("keycloak").setTokenUri("https://static.example.test/token");
		LazyClientRegistrationRepository repository = repository(properties);

		assertThat(repository.findByRegistrationId("keycloak").getProviderDetails().getTokenUri())
			.isEqualTo("https://static.example.test/token");
		assertThat(repository.states()).isEmpty();
		assertThat(this.fetches).hasValue(0);
	}

	@Test
	void returnsNullForAnUnknownRegistration() {
		assertThat(repository().findByRegistrationId("other")).isNull();
	}

	private static OAuth2ClientProperties properties(String tokenUri) {
		OAuth2ClientProperties properties = new OAuth2ClientProperties();
		OAuth2ClientProperties.Registration registration = new OAuth2ClientProperties.Registration();
		registration.setClientId("client-id");
		registration.setClientAuthenticationMethod("private_key_jwt");
		registration.setAuthorizationGrantType("authorization_code");
		registration.setRedirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}");
		registration.setScope(java.util.Set.of("openid"));
		properties.getRegistration().put("keycloak", registration);
		OAuth2ClientProperties.Provider provider = new OAuth2ClientProperties.Provider();
		provider.setIssuerUri(ISSUER);
		provider.setUserNameAttribute("preferred_username");
		provider.setTokenUri(tokenUri);
		properties.getProvider().put("keycloak", provider);
		return properties;
	}

	private static Map<String, Object> metadata() {
		return Map.of("issuer", ISSUER, "authorization_endpoint", ISSUER + "/auth", "token_endpoint", ISSUER + "/token",
				"jwks_uri", ISSUER + "/certs", "userinfo_endpoint", ISSUER + "/userinfo", "end_session_endpoint",
				ISSUER + "/logout", "response_types_supported", List.of("code"), "subject_types_supported",
				List.of("public"), "id_token_signing_alg_values_supported", List.of("RS256"),
				"token_endpoint_auth_methods_supported", List.of("private_key_jwt"));
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
