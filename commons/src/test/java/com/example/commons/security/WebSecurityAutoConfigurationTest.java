package com.example.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.session.FindByIndexNameSessionRepository;

import com.example.commons.security.oauth2.OidcIdTokenDecoders;
import com.example.commons.security.authentication.oidc.LocalAuthoritiesOidcUserService;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.oauth2.IdTokenDecryption;
import com.example.commons.security.oauth2.JwksController;
import com.example.commons.security.oauth2.JwksHealthIndicator;
import com.example.commons.security.oauth2.PrivateKeyJwtAutoConfiguration;
import com.example.commons.security.oauth2.RefreshingJwks;
import com.example.commons.security.session.SessionRevocationService;

class WebSecurityAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class,
						WebSecurityAutoConfiguration.class, PrivateKeyJwtAutoConfiguration.class))
		.withPropertyValues("commons.security.session.absolute-timeout=12h")
		.withUserConfiguration(ApplicationBeansConfiguration.class);

	@Test
	void appliesTheSecurityBaselineByDefault() {
		this.contextRunner.withUserConfiguration(LocalAuthorityLookupConfiguration.class)
			.run(context -> assertThat(context).hasNotFailed()
				.hasSingleBean(LocalAuthoritiesOidcUserService.class)
				.hasSingleBean(SessionRevocationService.class)
				.hasSingleBean(SecurityAuditEventLogger.class)
				.hasBean("securityFilterChainCustomizer")
				.doesNotHaveBean(JwksController.class)
				.doesNotHaveBean(RefreshingJwks.class)
				.doesNotHaveBean(IdTokenDecryption.class));
	}

	@Test
	void failsWithoutALocalAuthorityLookup() {
		this.contextRunner.run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.hasMessageContaining("requires a LocalAuthorityLookup bean"));
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner
			.withPropertyValues("commons.security.enabled=false",
					"spring.security.oauth2.client.registration.test.client-authentication-method=private_key_jwt")
			.run(context -> assertThat(context).hasNotFailed()
				.doesNotHaveBean(WebSecurityAutoConfiguration.class)
				.doesNotHaveBean(PrivateKeyJwtAutoConfiguration.class)
				.doesNotHaveBean(Customizer.class));
	}

	@Test
	void configuresPrivateKeyJwtWhenAClientRegistrationUsesIt() {
		this.contextRunner.withUserConfiguration(LocalAuthorityLookupConfiguration.class)
			.withPropertyValues(
					"spring.security.oauth2.client.registration.test.client-authentication-method=private_key_jwt",
					"commons.security.oauth2.jwks=classpath:jwks.json")
			.run(context -> assertThat(context).hasNotFailed()
				.hasSingleBean(JwksController.class)
				.hasSingleBean(RefreshingJwks.class)
				.hasSingleBean(JwksHealthIndicator.class)
				.hasSingleBean(JwtDecoderFactory.class)
				.hasSingleBean(IdTokenDecryption.class)
				.hasBean("privateKeyJwtFilterChainCustomizer"));
	}

	@Test
	void requiresAJwksWhenAClientRegistrationUsesPrivateKeyJwt() {
		this.contextRunner.withUserConfiguration(LocalAuthorityLookupConfiguration.class)
			.withPropertyValues(
					"spring.security.oauth2.client.registration.test.client-authentication-method=private_key_jwt")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("commons.security.oauth2.jwks"));
	}

	@Test
	void redirectsBrowsersToTheOnlyClientRegistration() {
		assertThat(WebSecurityAutoConfiguration
			.authorizationRequestUri(new InMemoryClientRegistrationRepository(clientRegistration("keycloak"))))
			.isEqualTo("/oauth2/authorization/keycloak");
	}

	@Test
	void redirectsBrowsersToTheLoginPageWhenThereAreSeveralClientRegistrations() {
		assertThat(WebSecurityAutoConfiguration.authorizationRequestUri(
				new InMemoryClientRegistrationRepository(clientRegistration("keycloak"), clientRegistration("other"))))
			.isEqualTo("/login");
	}

	@Test
	void oidcIdTokenValidatorRejectsUnexpectedIssuerAudienceAndAuthorizedParty() {
		ClientRegistration clientRegistration = clientRegistration("test");
		Instant now = Instant.now();
		Jwt validIdToken = idToken(now, "https://issuer.example.test", List.of("client-id"), null);
		Jwt unexpectedIssuer = idToken(now, "https://other-issuer.example.test", List.of("client-id"), null);
		Jwt unexpectedAudience = idToken(now, "https://issuer.example.test", List.of("other-client"), null);
		Jwt unexpectedAuthorizedParty = idToken(now, "https://issuer.example.test",
				List.of("client-id", "other-client"), "other-client");

		assertThat(OidcIdTokenDecoders.oidcIdTokenValidator(clientRegistration).validate(validIdToken).hasErrors())
			.isFalse();
		assertThat(OidcIdTokenDecoders.oidcIdTokenValidator(clientRegistration).validate(unexpectedIssuer).hasErrors())
			.isTrue();
		assertThat(
				OidcIdTokenDecoders.oidcIdTokenValidator(clientRegistration).validate(unexpectedAudience).hasErrors())
			.isTrue();
		assertThat(OidcIdTokenDecoders.oidcIdTokenValidator(clientRegistration)
			.validate(unexpectedAuthorizedParty)
			.hasErrors()).isTrue();
	}

	private static ClientRegistration clientRegistration(String registrationId) {
		return ClientRegistration.withRegistrationId(registrationId)
			.clientId("client-id")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("https://client.example.test/login/oauth2/code/" + registrationId)
			.authorizationUri("https://issuer.example.test/authorize")
			.tokenUri("https://issuer.example.test/token")
			.jwkSetUri("https://issuer.example.test/jwks")
			.issuerUri("https://issuer.example.test")
			.build();
	}

	private static Jwt idToken(Instant now, String issuer, List<String> audience, String authorizedParty) {
		Jwt.Builder builder = Jwt.withTokenValue("id-token")
			.header("alg", "RS256")
			.issuer(issuer)
			.subject("subject")
			.audience(audience)
			.issuedAt(now.minusSeconds(1))
			.expiresAt(now.plusSeconds(60));
		if (authorizedParty != null) {
			builder.claim("azp", authorizedParty);
		}
		return builder.build();
	}

	/**
	 * Beans an application gets from Spring Boot's OAuth2 client and Spring Session
	 * auto-configuration.
	 */
	@Configuration(proxyBeanMethods = false)
	static class ApplicationBeansConfiguration {

		@Bean
		ClientRegistrationRepository clientRegistrationRepository() {
			return new InMemoryClientRegistrationRepository(clientRegistration("test"));
		}

		@Bean
		@SuppressWarnings("unchecked")
		FindByIndexNameSessionRepository<?> sessionRepository() {
			return mock(FindByIndexNameSessionRepository.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class LocalAuthorityLookupConfiguration {

		@Bean
		LocalAuthorityLookup localAuthorityLookup() {
			return username -> Optional.empty();
		}

	}

}
