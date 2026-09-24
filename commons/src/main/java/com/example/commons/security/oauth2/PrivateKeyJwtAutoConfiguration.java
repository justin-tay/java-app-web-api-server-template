package com.example.commons.security.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
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
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;

/**
 * Configures {@code private_key_jwt} client authentication (see docs/adr/0007): loads the
 * deployment's private JWKS from {@code commons.security.oauth2.jwks}, signs the client
 * assertion sent to the token endpoint with its signing key, and publishes the public
 * keys at {@value JwksController#JWKS_PATH} for the identity provider to verify that
 * assertion.
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
	 * Gets the JWKS for encryption/decryption and signing/verification.
	 * @param resourceLoader the resource loader
	 * @param properties the JWKS properties
	 * @return the JWKS
	 * @throws ParseException if the resource is not a valid JWKS
	 */
	@Bean
	JWKSet jwks(ResourceLoader resourceLoader, JwksProperties properties) throws ParseException {
		String location = properties.getJwks();
		try (InputStream inputStream = resourceLoader.getResource(location).getInputStream()) {
			return JWKSet.load(inputStream);
		}
		catch (IOException ex) {
			throw new IllegalStateException(
					"Unable to read the private JWKS configured by commons.security.oauth2.jwks: " + location, ex);
		}
	}

	@Bean
	JwksController jwksController(JWKSet jwks) {
		return new JwksController(jwks);
	}

	/**
	 * Signs the token request's client assertion with the JWKS signing key, and lets the
	 * identity provider fetch the public keys without authenticating.
	 * @param jwks the JWKS
	 * @return the customizer
	 */
	@Bean
	@Order(WebSecurityAutoConfiguration.FILTER_CHAIN_CUSTOMIZER_ORDER)
	Customizer<HttpSecurity> privateKeyJwtFilterChainCustomizer(JWKSet jwks) {
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
	 * authentication.
	 * @param jwks the JWKS
	 * @return the access token response client
	 */
	private static OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient(
			JWKSet jwks) {
		Function<ClientRegistration, JWK> jwkResolver = clientRegistration -> jwks.getKeys()
			.stream()
			.filter(jwk -> KeyUse.SIGNATURE.equals(jwk.getKeyUse()))
			.findFirst()
			.get();
		NimbusJwtClientAuthenticationParametersConverter<OAuth2AuthorizationCodeGrantRequest> parametersConverter = new NimbusJwtClientAuthenticationParametersConverter<>(
				jwkResolver);
		RestClientAuthorizationCodeTokenResponseClient accessTokenResponseClient = new RestClientAuthorizationCodeTokenResponseClient();
		accessTokenResponseClient.addParametersConverter(parametersConverter);
		return accessTokenResponseClient;
	}

}
