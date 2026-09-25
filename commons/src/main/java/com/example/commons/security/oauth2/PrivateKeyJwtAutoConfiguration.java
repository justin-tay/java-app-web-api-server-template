package com.example.commons.security.oauth2;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.example.commons.security.WebSecurityAutoConfiguration;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.proc.JWEKeySelector;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Configures {@code private_key_jwt} client authentication (see docs/adr/0007 and
 * docs/adr/0020): reads the deployment's private JWKS from the locations in
 * {@code commons.security.oauth2.jwks} and reads them again every
 * {@code commons.security.oauth2.jwks-refresh-interval}, signs the client assertion sent
 * to the token endpoint with the current signing key, decrypts ID tokens encrypted to its
 * {@code enc} keys, if any, and publishes the public keys at
 * {@value JwksController#JWKS_PATH} for the identity provider to verify that assertion
 * and encrypt to.
 *
 * <p>
 * Applied only when commons security is on and at least one
 * {@code spring.security.oauth2.client.registration.*} uses
 * {@code client-authentication-method: private_key_jwt}. It then requires
 * {@code commons.security.oauth2.jwks}, and startup fails when it is unset.
 */
@AutoConfiguration(after = WebSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnBooleanProperty(name = "commons.security.enabled", matchIfMissing = true)
@ConditionalOnPrivateKeyJwtClientRegistration
@EnableConfigurationProperties(JwksProperties.class)
public class PrivateKeyJwtAutoConfiguration {

	/**
	 * Gets the private JWKS, read from its locations now and on a schedule.
	 * @param resourceLoader the resource loader
	 * @param properties the JWKS properties
	 * @return the JWKS
	 */
	@Bean
	RefreshingJwks jwks(ResourceLoader resourceLoader, JwksProperties properties) {
		List<Resource> locations = properties.getJwks().stream().map(resourceLoader::getResource).toList();
		return new RefreshingJwks(locations, properties.getJwksRefreshInterval(), Clock.systemUTC());
	}

	@Bean
	JwksController jwksController(RefreshingJwks jwks) {
		return new JwksController(jwks);
	}

	/**
	 * Decrypts ID tokens encrypted to the JWKS {@code enc} keys, and requires encrypted
	 * ID tokens while there are any, so an identity provider client whose ID token
	 * encryption was turned off fails loudly rather than being accepted.
	 * @param jwks the JWKS
	 * @return the ID token decryption
	 */
	@Bean
	IdTokenDecryption idTokenDecryption(RefreshingJwks jwks) {
		JwksDecryptionKeySelector keySelector = new JwksDecryptionKeySelector(jwks);
		return new IdTokenDecryption() {

			@Override
			public JWEKeySelector<SecurityContext> keySelector() {
				return keySelector;
			}

			@Override
			public boolean isRequired() {
				return jwks.hasEncryptionKeys();
			}

		};
	}

	/**
	 * Signs the token request's client assertion with the JWKS signing key, and lets the
	 * identity provider fetch the public keys without authenticating.
	 * @param jwks the JWKS
	 * @return the customizer
	 */
	@Bean
	@Order(WebSecurityAutoConfiguration.FILTER_CHAIN_CUSTOMIZER_ORDER)
	Customizer<HttpSecurity> privateKeyJwtFilterChainCustomizer(RefreshingJwks jwks) {
		OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient = accessTokenResponseClient(
				jwks);
		return http -> http
			.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(JwksController.JWKS_PATH))
				.anonymous())
			.oauth2Login(oauth2Login -> oauth2Login
				.tokenEndpoint(tokenEndpoint -> tokenEndpoint.accessTokenResponseClient(accessTokenResponseClient)));
	}

	/**
	 * Gets the access token response client configured for {@code private_key_jwt}
	 * authentication. The signing key is resolved for every token request, so a rotated
	 * key is used as soon as the JWKS is refreshed; when there is none the token request
	 * fails.
	 * @param jwks the JWKS
	 * @return the access token response client
	 */
	private static OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient(
			RefreshingJwks jwks) {
		Function<ClientRegistration, JWK> jwkResolver = clientRegistration -> jwks.signingKey().orElse(null);
		NimbusJwtClientAuthenticationParametersConverter<OAuth2AuthorizationCodeGrantRequest> parametersConverter = new NimbusJwtClientAuthenticationParametersConverter<>(
				jwkResolver);
		RestClientAuthorizationCodeTokenResponseClient accessTokenResponseClient = new RestClientAuthorizationCodeTokenResponseClient();
		accessTokenResponseClient.addParametersConverter(parametersConverter);
		return accessTokenResponseClient;
	}

	/**
	 * Reports the JWKS to the readiness group when actuator health is present.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	static class JwksHealthConfiguration {

		@Bean
		JwksHealthIndicator jwksHealthIndicator(RefreshingJwks jwks) {
			return new JwksHealthIndicator(jwks);
		}

	}

}
