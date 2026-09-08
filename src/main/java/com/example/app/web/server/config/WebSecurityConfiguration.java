package com.example.app.web.server.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.example.app.web.server.security.ProblemDetailAccessDeniedHandler;
import com.example.app.web.server.security.RequestLoggingFilter;
import com.example.app.web.server.security.SecurityLoggingContextFilter;
import com.example.app.web.server.security.LocalAuthoritiesOidcUserService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Web security configuration.
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

	private static final String CONTENT_SECURITY_POLICY = "base-uri 'none';default-src 'none';form-action 'none';frame-ancestors 'none'";

	private static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=(), payment=(), usb=()";

	private static final List<String> QUERY_PARAMETER_REDACT_LIST = List.of("access_token", "client_assertion",
			"client_secret", "code", "code_verifier", "id_token", "id_token_hint", "logout_token", "refresh_token",
			"session_state", "state");

	private final Map<String, JwtDecoder> jwtDecoders = new ConcurrentHashMap<>();

	/**
	 * Gets the JWKS for encryption/decryption and signing/verification.
	 * @param resourceLoader the resource loader
	 * @param applicationProperties the application properties
	 * @return the JWKS
	 * @throws IOException the exception
	 * @throws ParseException the exception
	 */
	@Bean
	JWKSet jwks(ResourceLoader resourceLoader, ApplicationProperties applicationProperties)
			throws IOException, ParseException {
		try (InputStream inputStream = resourceLoader.getResource(applicationProperties.getJwks()).getInputStream()) {
			return JWKSet.load(inputStream);
		}
	}

	/**
	 * Configure the security filter chain.
	 * @param http the http security
	 * @param jwks the JWKS
	 * @param clientRegistrationRepository the client registration repository
	 * @return the security filter chain
	 * @throws Exception the exception
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JWKSet jwks,
			ClientRegistrationRepository clientRegistrationRepository,
			AuthenticationEventPublisher authenticationEventPublisher,
			SecurityLoggingContextFilter securityLoggingContextFilter, ApplicationProperties applicationProperties,
			LocalAuthoritiesOidcUserService localAuthoritiesOidcUserService) throws Exception {
		http.getSharedObject(AuthenticationManagerBuilder.class)
			.authenticationEventPublisher(authenticationEventPublisher);
		OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient = accessTokenResponseClient(
				jwks);
		RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter(QUERY_PARAMETER_REDACT_LIST);
		return http.addFilterAfter(securityLoggingContextFilter, SecurityContextHolderFilter.class)
			.addFilterAfter(requestLoggingFilter, SecurityLoggingContextFilter.class)
			.headers(headers -> headers
				.contentSecurityPolicy(
						contentSecurityPolicy -> contentSecurityPolicy.policyDirectives(CONTENT_SECURITY_POLICY))
				.referrerPolicy(referrerPolicy -> referrerPolicy
					.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.permissionsPolicyHeader(permissionsPolicy -> permissionsPolicy.policy(PERMISSIONS_POLICY)))
			.exceptionHandling(
					exceptionHandling -> exceptionHandling.accessDeniedHandler(new ProblemDetailAccessDeniedHandler()))
			.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/admin/users/**"))
				.hasRole("USER_MANAGE")
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/admin/groups/**"))
				.hasRole("GROUP_MANAGE")
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/admin/roles/**"))
				.hasAuthority("ROLE_ROLE_MANAGE"))
			.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/oauth2/jwks"))
				.anonymous())
			.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/**"))
				.authenticated())
			.oauth2Login(oauth2Login -> oauth2Login
				.tokenEndpoint(tokenEndpoint -> tokenEndpoint.accessTokenResponseClient(accessTokenResponseClient))
				.userInfoEndpoint(
						userInfoEndpoint -> userInfoEndpoint.oidcUserService(localAuthoritiesOidcUserService)))
			.oidcLogout(oidcLogout -> oidcLogout.backChannel(withDefaults()))
			.logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
			.with(new DefaultLoginPageConfigurer<>(),
					defaultLoginPage -> defaultLoginPage.withObjectPostProcessor(new ObjectPostProcessor<Object>() {
						@Override
						public <O> O postProcess(O object) {
							if (object instanceof DefaultLoginPageGeneratingFilter filter) {
								// Configure this so the default login page generates the
								// logout message after
								// the post logout redirect
								filter.setLogoutSuccessUrl("/login?logout");
							}
							return object;
						}
					}))
			.build();
	}

	/**
	 * Publishes application events for successful and failed authentication attempts.
	 * @param applicationEventPublisher publishes application events
	 * @return the authentication event publisher
	 */
	@Bean
	AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		DefaultAuthenticationEventPublisher authenticationEventPublisher = new DefaultAuthenticationEventPublisher(
				applicationEventPublisher);
		authenticationEventPublisher.setDefaultAuthenticationFailureEvent(AbstractAuthenticationFailureEvent.class);
		return authenticationEventPublisher;
	}

	/**
	 * Publishes application events for authorization denials.
	 * @param applicationEventPublisher publishes application events
	 * @return the authorization event publisher
	 */
	@Bean
	AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return new SpringAuthorizationEventPublisher(applicationEventPublisher);
	}

	/**
	 * Configure the JWT decoder used to decode the ID Token.
	 * @return the jwt decoder factory to decode the ID Token
	 */
	@Bean
	JwtDecoderFactory<ClientRegistration> jwtDecoderFactory() {
		/*
		 * The default implementation is OidcIdTokenDecoderFactory but its customization
		 * is limited for instance if the id token needs to be decrypted.
		 */
		return clientRegistration -> {
			return jwtDecoders.computeIfAbsent(clientRegistration.getRegistrationId(), key -> {
				DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
				JWSVerificationKeySelector<SecurityContext> jwsKeySelector;
				JWKSource<SecurityContext> jwkSource = jwkSource(clientRegistration);
				jwsKeySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new JWKSource<SecurityContext>() {
					@Override
					public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) throws KeySourceException {
						List<JWK> jwk = jwkSource.get(jwkSelector, context);
						return jwk.stream().filter(key -> {
							return KeyUse.SIGNATURE.equals(key.getKeyUse());
						}).toList();
					}
				});
				jwtProcessor.setJWSKeySelector(jwsKeySelector);
				NimbusJwtDecoder jwtDecoder = new NimbusJwtDecoder(jwtProcessor);
				jwtDecoder.setJwtValidator(oidcIdTokenValidator(clientRegistration));
				return jwtDecoder;
			});
		};
	}

	/**
	 * Gets the OpenID Connect validator for an ID token.
	 * @param clientRegistration the client registration that received the ID token
	 * @return the validator for required OpenID Connect ID token claims
	 */
	static OAuth2TokenValidator<Jwt> oidcIdTokenValidator(ClientRegistration clientRegistration) {
		return new OidcIdTokenValidator(clientRegistration);
	}

	/**
	 * Gets the jwk source to use for a client registration.
	 * @param clientRegistration the client registration
	 * @return the jwk source
	 */
	private JWKSource<SecurityContext> jwkSource(ClientRegistration clientRegistration) {
		String jwkSetUri = clientRegistration.getProviderDetails().getJwkSetUri();
		try {
			return JWKSourceBuilder.create(new URL(jwkSetUri)).retrying(true).build();
		}
		catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Gets the oidc logout success handler that calls the OpenID end_session_endpoint.
	 * @param clientRegistrationRepository
	 * @return
	 */
	private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
		OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(
				clientRegistrationRepository);
		oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/login?logout");
		return oidcLogoutSuccessHandler;
	}

	/**
	 * Gets the access token response client configured for private_key_jwt
	 * authentication.
	 * @param jwks the JWKS
	 * @return the access token response client
	 */
	private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient(
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

	/**
	 * Gets the OAuth2UserService that processes the tokens to add the granted
	 * authorities.
	 * @return the oidc user service
	 */

}
