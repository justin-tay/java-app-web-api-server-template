package com.example.commons.security.oauth2;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Gives each call the application makes to an OpenID Provider the trust material of that
 * provider's {@code commons.security.oauth2.client.provider.<id>.ssl-bundle}, if it has
 * one (see docs/adr/0029). Spring Security configures none of these calls from an SSL
 * bundle, and its own issuer discovery cannot be configured at all, so each one is built
 * here: discovery, the token endpoint, the ID token's JWK Set and the user info endpoint.
 * A provider without a bundle keeps Spring Security's own client, so nothing changes for
 * it.
 */
public class ProviderTrust {

	private final Map<String, SslBundle> bundlesByProvider = new HashMap<>();

	private final Map<String, String> providerByRegistration = new HashMap<>();

	private final Map<String, String> providerByIssuerUri = new HashMap<>();

	/**
	 * Resolves the configured bundles now, so an unknown bundle or provider ID stops
	 * startup rather than silently leaving a provider on the JVM's default trust.
	 * @param clientProperties Spring Boot's OAuth2 client properties
	 * @param properties the per-provider settings
	 * @param sslBundles the SSL bundles
	 * @throws IllegalStateException when a provider ID matches no provider
	 */
	public ProviderTrust(OAuth2ClientProperties clientProperties, OAuth2ClientProviderProperties properties,
			SslBundles sslBundles) {
		clientProperties.getRegistration().forEach((registrationId, registration) -> {
			String providerId = (registration.getProvider() != null) ? registration.getProvider() : registrationId;
			this.providerByRegistration.put(registrationId, providerId);
		});
		clientProperties.getProvider().forEach((providerId, provider) -> {
			if (provider.getIssuerUri() != null) {
				this.providerByIssuerUri.put(provider.getIssuerUri(), providerId);
			}
		});
		properties.getProvider().forEach((providerId, provider) -> {
			if (!clientProperties.getProvider().containsKey(providerId)
					&& !this.providerByRegistration.containsValue(providerId)) {
				throw new IllegalStateException("commons.security.oauth2.client.provider." + providerId
						+ " matches no provider in spring.security.oauth2.client");
			}
			if (StringUtils.hasText(provider.getSslBundle())) {
				this.bundlesByProvider.put(providerId, sslBundles.getBundle(provider.getSslBundle()));
			}
		});
	}

	/**
	 * Gets the bundle for the provider of a client registration.
	 * @param registrationId the registration ID
	 * @return the bundle, or empty to use the JVM's default trust
	 */
	public Optional<SslBundle> forRegistration(String registrationId) {
		return Optional.ofNullable(this.bundlesByProvider.get(this.providerByRegistration.get(registrationId)));
	}

	/**
	 * Gets the bundle for the provider with an {@code issuer-uri}.
	 * @param issuerUri the issuer URI as configured
	 * @return the bundle, or empty to use the JVM's default trust
	 */
	public Optional<SslBundle> forIssuerUri(String issuerUri) {
		return Optional.ofNullable(this.bundlesByProvider.get(this.providerByIssuerUri.get(issuerUri)));
	}

	/**
	 * Builds a request factory that trusts a bundle. It speaks HTTP/1.1, so a plain
	 * {@code http://} issuer is never offered an upgrade, and it does not follow
	 * redirects: an identity provider's endpoints answer where they are configured, and a
	 * redirect from one is a misconfiguration to report, not to follow.
	 * @param bundle the bundle, or null for the JVM's default trust
	 * @param connectTimeout the connect timeout, or null for none
	 * @param readTimeout the read timeout, or null for none
	 * @return the request factory
	 */
	static JdkClientHttpRequestFactory requestFactory(SslBundle bundle, Duration connectTimeout, Duration readTimeout) {
		HttpClient.Builder builder = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.followRedirects(HttpClient.Redirect.NEVER);
		if (bundle != null) {
			builder.sslContext(bundle.createSslContext());
		}
		if (connectTimeout != null) {
			builder.connectTimeout(connectTimeout);
		}
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(builder.build());
		if (readTimeout != null) {
			requestFactory.setReadTimeout(readTimeout);
		}
		return requestFactory;
	}

	/**
	 * Gets a token client for the authorization code grant that uses a registration's
	 * provider bundle, and Spring Security's own client for a provider without one.
	 * @param customizer applied to every client it builds, for instance to add the
	 * {@code private_key_jwt} client assertion
	 * @return the token client
	 */
	public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient(
			Consumer<RestClientAuthorizationCodeTokenResponseClient> customizer) {
		Function<String, RestClientAuthorizationCodeTokenResponseClient> clients = perRegistration(
				tokenClient(null, customizer), bundle -> tokenClient(bundle, customizer));
		return request -> clients.apply(request.getClientRegistration().getRegistrationId()).getTokenResponse(request);
	}

	/**
	 * Gets a user info service that uses a registration's provider bundle, and Spring
	 * Security's own for a provider without one.
	 * @return the user info service
	 */
	public OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
		Function<String, DefaultOAuth2UserService> services = perRegistration(userService(null),
				ProviderTrust::userService);
		return request -> services.apply(request.getClientRegistration().getRegistrationId()).loadUser(request);
	}

	/**
	 * Gets, for a registration ID, the object built for its provider's bundle (once), or
	 * the fallback when the provider has none.
	 */
	private <T> Function<String, T> perRegistration(T fallback, Function<SslBundle, T> forBundle) {
		Map<String, T> built = new ConcurrentHashMap<>();
		return registrationId -> forRegistration(registrationId)
			.map(bundle -> built.computeIfAbsent(registrationId, key -> forBundle.apply(bundle)))
			.orElse(fallback);
	}

	private static RestClientAuthorizationCodeTokenResponseClient tokenClient(SslBundle bundle,
			Consumer<RestClientAuthorizationCodeTokenResponseClient> customizer) {
		RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
		if (bundle != null) {
			client.setRestClient(tokenRestClient(bundle));
		}
		customizer.accept(client);
		return client;
	}

	private static DefaultOAuth2UserService userService(SslBundle bundle) {
		DefaultOAuth2UserService service = new DefaultOAuth2UserService();
		if (bundle != null) {
			RestTemplate restTemplate = new RestTemplate(requestFactory(bundle, null, null));
			restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
			service.setRestOperations(restTemplate);
		}
		return service;
	}

	/**
	 * Builds the client Spring Security's token client would build for itself, over a
	 * request factory that trusts the bundle.
	 */
	private static RestClient tokenRestClient(SslBundle bundle) {
		return RestClient.builder()
			.requestFactory(requestFactory(bundle, null, null))
			.configureMessageConverters(converters -> {
				converters.registerDefaults();
				converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
			})
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
			.build();
	}

}
