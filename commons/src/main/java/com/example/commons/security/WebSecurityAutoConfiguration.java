package com.example.commons.security;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.session.InMemoryOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

import com.example.commons.logging.LoggingAutoConfiguration;
import com.example.commons.security.authentication.ProblemDetailAuthenticationEntryPoint;
import com.example.commons.security.authentication.oidc.LocalAuthoritiesOidcUserService;
import com.example.commons.security.authentication.oidc.MaxAgeAuthorizationRequestResolver;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.authorization.LocalAuthorityRefreshFilter;
import com.example.commons.security.authorization.ProblemDetailAccessDeniedHandler;
import com.example.commons.security.firewall.ProblemDetailRequestRejectedHandler;
import com.example.commons.security.oauth2.IdTokenDecryption;
import com.example.commons.security.session.AbsoluteSessionTimeoutFilter;
import com.example.commons.security.session.AuditingInvalidSessionStrategy;
import com.example.commons.security.session.ContentNegotiatingSessionExpiredStrategy;
import com.example.commons.security.session.SessionLifecycleAuditInitializationFilter;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionLifecycleLogoutHandler;
import com.example.commons.security.session.SessionRepositoryOidcBackChannelLogoutHandler;
import com.example.commons.security.session.SecureRandomSessionIdGenerator;
import com.example.commons.security.session.SessionRevocationService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Configures the security baseline every application's Spring Security filter chain
 * shares: OIDC login with locally managed authorities, security response headers, RFC
 * 9457 Problem Details for authentication, authorization, session, and firewall failures,
 * JDBC-backed session management with an absolute timeout and a single session per user,
 * session lifecycle and security audit logging, OIDC back-channel and RP-initiated
 * logout, and method security.
 *
 * <p>
 * The filter chain settings are applied through a {@code Customizer<HttpSecurity>} bean,
 * which Spring Security applies to each {@code HttpSecurity} before the application's own
 * {@code SecurityFilterChain} bean method configures it. An application's chain therefore
 * holds only its own authorization rules. Once any authorization rule is configured,
 * which this configuration always does for the health endpoint, Spring Security denies
 * every request that no rule matches, so an application that omits its final
 * {@code anyRequest().authenticated()} rule fails closed.
 *
 * <p>
 * Applied whenever commons is on the classpath of a servlet application. Set
 * {@code commons.security.enabled=false} to turn it off. Every application must define a
 * {@link LocalAuthorityLookup} bean.
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class, after = LoggingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ HttpSecurity.class, ClientRegistrationRepository.class, JdbcIndexedSessionRepository.class })
@ConditionalOnBooleanProperty(name = "commons.security.enabled", matchIfMissing = true)
@EnableConfigurationProperties(WebSecurityProperties.class)
@EnableMethodSecurity
public class WebSecurityAutoConfiguration {

	/**
	 * Order of {@link #securityFilterChainCustomizer}: after the commons logging
	 * customizer, whose filters share positions with the filters added here.
	 */
	public static final int FILTER_CHAIN_CUSTOMIZER_ORDER = LoggingAutoConfiguration.FILTER_CHAIN_CUSTOMIZER_ORDER
			+ 100;

	private static final String CONTENT_SECURITY_POLICY = "base-uri 'none';default-src 'none';form-action 'none';frame-ancestors 'none'";

	private static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=(), payment=(), usb=()";

	private static final String LOGIN_PAGE_URI = "/login";

	private static final String LOGOUT_SUCCESS_URI = LOGIN_PAGE_URI + "?logout";

	@Bean
	@ConditionalOnMissingBean
	SessionLifecycleAuditLogger sessionLifecycleAuditLogger() {
		return new SessionLifecycleAuditLogger();
	}

	@Bean
	SecurityAuditEventLogger securityAuditEventLogger(SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		return new SecurityAuditEventLogger(sessionLifecycleAuditLogger);
	}

	/**
	 * Provides session IDs with 256 random bits, which Spring Session's JDBC
	 * configuration uses in place of its version 4 UUIDs.
	 * @return the session ID generator
	 */
	@Bean
	@ConditionalOnMissingBean
	SessionIdGenerator sessionIdGenerator() {
		return new SecureRandomSessionIdGenerator();
	}

	/**
	 * Provides the session registry used to enforce the concurrent-session limit.
	 * @param sessionRepository the Spring Session repository
	 * @return the Spring Session-backed registry
	 */
	@Bean
	@ConditionalOnMissingBean
	SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
		return new SpringSessionBackedSessionRegistry<>(sessionRepository);
	}

	/**
	 * Provides the response strategy for sessions expired by the concurrent-session
	 * limit.
	 * @param sessionLifecycleAuditLogger the session lifecycle audit logger
	 * @return the content-negotiating expiry strategy
	 */
	@Bean
	@ConditionalOnMissingBean
	SessionInformationExpiredStrategy sessionExpiredStrategy(SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		return new ContentNegotiatingSessionExpiredStrategy(sessionLifecycleAuditLogger);
	}

	/**
	 * Provides the registry that links an OpenID Provider session (its {@code sid} and
	 * {@code sub}) to the local session created at login, so a back-channel logout token
	 * can be resolved to the local session it ends. It is held in memory, so it only
	 * resolves sessions that logged in through this instance; see the "Back-channel
	 * logout" section of
	 * docs/system-design/08-crosscutting-concepts/02-security-and-authentication/authentication.md.
	 * @return the in-memory OIDC session registry
	 */
	@Bean
	@ConditionalOnMissingBean
	OidcSessionRegistry oidcSessionRegistry() {
		return new InMemoryOidcSessionRegistry();
	}

	@Bean
	SessionRevocationService sessionRevocationService(SessionRegistry sessionRegistry,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		return new SessionRevocationService(sessionRegistry, sessionRepository, sessionLifecycleAuditLogger);
	}

	/**
	 * Publishes application events for successful and failed authentication attempts.
	 * @param applicationEventPublisher publishes application events
	 * @return the authentication event publisher
	 */
	@Bean
	@ConditionalOnMissingBean
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
	@ConditionalOnMissingBean
	AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return new SpringAuthorizationEventPublisher(applicationEventPublisher);
	}

	/**
	 * Configures the JWT decoder used to decode the ID Token. The default
	 * {@code OidcIdTokenDecoderFactory} offers limited customization, for instance if the
	 * ID token needs to be decrypted: when an {@link IdTokenDecryption} bean exists, as
	 * it does for a {@code private_key_jwt} client with {@code enc} keys (see
	 * docs/adr/0020), an ID token encrypted to the application is decrypted before its
	 * signature is verified, and a plain signed ID token is rejected while encryption is
	 * required.
	 * @param idTokenDecryption the ID token decryption, if any
	 * @return the JWT decoder factory to decode the ID Token
	 */
	@Bean
	@ConditionalOnMissingBean
	JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(ObjectProvider<IdTokenDecryption> idTokenDecryption) {
		Map<String, JwtDecoder> jwtDecoders = new ConcurrentHashMap<>();
		return clientRegistration -> jwtDecoders.computeIfAbsent(clientRegistration.getRegistrationId(), key -> {
			JWKSource<SecurityContext> jwkSource = jwkSource(clientRegistration);
			DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
			jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
					(jwkSelector, context) -> jwkSource.get(jwkSelector, context)
						.stream()
						.filter(jwk -> KeyUse.SIGNATURE.equals(jwk.getKeyUse()))
						.toList()));
			IdTokenDecryption decryption = idTokenDecryption.getIfAvailable();
			if (decryption != null) {
				jwtProcessor.setJWEKeySelector(decryption.keySelector());
			}
			NimbusJwtDecoder jwtDecoder = new NimbusJwtDecoder(jwtProcessor);
			jwtDecoder.setJwtValidator(oidcIdTokenValidator(clientRegistration));
			if (decryption == null) {
				return jwtDecoder;
			}
			return token -> {
				if (decryption.isRequired() && !isJwe(token)) {
					throw new BadJwtException(
							"The ID token is not encrypted, but this client requires encrypted ID tokens");
				}
				return jwtDecoder.decode(token);
			};
		});
	}

	/**
	 * Returns whether a token is in JWE compact serialization, which has five parts where
	 * a JWS has three.
	 * @param token the token
	 * @return whether the token is a JWE
	 */
	private static boolean isJwe(String token) {
		return token.chars().filter(character -> character == '.').count() == 4;
	}

	/**
	 * Logs a request rejected by Spring Security's {@code HttpFirewall} (the default
	 * {@code StrictHttpFirewall}) in the application's structured logging format and
	 * returns an RFC 9457 Problem Details response, instead of the framework default of a
	 * bare {@code sendError(400)} logged at {@code DEBUG} through commons-logging.
	 * @return the customizer that installs {@link ProblemDetailRequestRejectedHandler}
	 */
	@Bean
	WebSecurityCustomizer requestRejectedHandlerCustomizer() {
		return web -> web.requestRejectedHandler(new ProblemDetailRequestRejectedHandler());
	}

	@Bean
	LocalAuthoritiesOidcUserService localAuthoritiesOidcUserService(
			ObjectProvider<LocalAuthorityLookup> localAuthorityLookup) {
		return new LocalAuthoritiesOidcUserService(requireLocalAuthorityLookup(localAuthorityLookup));
	}

	/**
	 * Applies the security baseline to every {@code HttpSecurity}.
	 *
	 * <p>
	 * The filters are positioned relative to Spring Security's own filters, keeping this
	 * order: {@code AuthenticatedUserLoggingContextFilter} (commons logging),
	 * {@link SessionLifecycleAuditInitializationFilter},
	 * {@link SecurityContextHolderFilter}, {@link AbsoluteSessionTimeoutFilter},
	 * {@code RequestLoggingFilter} (commons logging),
	 * {@link LocalAuthorityRefreshFilter}, {@link HeaderWriterFilter}. Filters that share
	 * a position keep the order they were added in, which is why this customizer runs
	 * after the logging one.
	 * @return the customizer
	 */
	@Bean
	@Order(FILTER_CHAIN_CUSTOMIZER_ORDER)
	Customizer<HttpSecurity> securityFilterChainCustomizer(WebSecurityProperties properties,
			ObjectProvider<Clock> clock, ClientRegistrationRepository clientRegistrationRepository,
			AuthenticationEventPublisher authenticationEventPublisher,
			LocalAuthoritiesOidcUserService localAuthoritiesOidcUserService,
			ObjectProvider<LocalAuthorityLookup> localAuthorityLookup, SessionRegistry sessionRegistry,
			SessionInformationExpiredStrategy sessionExpiredStrategy,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger, OidcSessionRegistry oidcSessionRegistry,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository, Environment environment) {
		LocalAuthorityLookup lookup = requireLocalAuthorityLookup(localAuthorityLookup);
		String healthPath = environment.getProperty("management.endpoints.web.base-path", "/actuator") + "/health";
		return http -> {
			http.getSharedObject(AuthenticationManagerBuilder.class)
				.authenticationEventPublisher(authenticationEventPublisher);
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint = new ProblemDetailAuthenticationEntryPoint(
					authorizationRequestUri(clientRegistrationRepository));
			ProblemDetailAccessDeniedHandler accessDeniedHandler = new ProblemDetailAccessDeniedHandler();
			// The request cache is a shared object set while the chain is built, so it is
			// resolved when a request arrives rather than now.
			AuditingInvalidSessionStrategy invalidSessionStrategy = new AuditingInvalidSessionStrategy(
					sessionLifecycleAuditLogger, () -> http.getSharedObject(RequestCache.class),
					authenticationEntryPoint, accessDeniedHandler);
			http.addFilterBefore(new SessionLifecycleAuditInitializationFilter(sessionLifecycleAuditLogger),
					SecurityContextHolderFilter.class)
				.addFilterAfter(
						new AbsoluteSessionTimeoutFilter(properties.getSession().getAbsoluteTimeout(),
								clock.getIfAvailable(Clock::systemDefaultZone), sessionLifecycleAuditLogger),
						SecurityContextHolderFilter.class)
				.addFilterBefore(new LocalAuthorityRefreshFilter(lookup, sessionLifecycleAuditLogger, sessionRegistry),
						HeaderWriterFilter.class)
				.headers(headers -> headers
					.contentSecurityPolicy(
							contentSecurityPolicy -> contentSecurityPolicy.policyDirectives(CONTENT_SECURITY_POLICY))
					.referrerPolicy(referrerPolicy -> referrerPolicy
						.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
					.permissionsPolicyHeader(permissionsPolicy -> permissionsPolicy.policy(PERMISSIONS_POLICY)))
				.exceptionHandling(exceptionHandling -> exceptionHandling.accessDeniedHandler(accessDeniedHandler)
					.authenticationEntryPoint(authenticationEntryPoint))
				// The management port (see docs/adr/0014) is a separate embedded server
				// that
				// nonetheless shares this filter chain, so it goes through these rules
				// too.
				// Only the health check the ALB/monitoring probes, and its liveness and
				// readiness groups (see docs/adr/0020), are unauthenticated; every
				// other actuator endpoint falls through to the application's rules, and
				// is also not exposed (see management.endpoints.web.exposure.include).
				.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
					.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(healthPath),
							PathPatternRequestMatcher.withDefaults().matcher(healthPath + "/liveness"),
							PathPatternRequestMatcher.withDefaults().matcher(healthPath + "/readiness"))
					.permitAll())
				.sessionManagement(sessionManagement -> sessionManagement.invalidSessionStrategy(invalidSessionStrategy)
					.maximumSessions(1)
					.maxSessionsPreventsLogin(false)
					.sessionRegistry(sessionRegistry)
					.expiredSessionStrategy(sessionExpiredStrategy))
				.oauth2Login(oauth2Login -> oauth2Login
					.authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint.authorizationRequestResolver(
							new MaxAgeAuthorizationRequestResolver(clientRegistrationRepository)))
					.userInfoEndpoint(
							userInfoEndpoint -> userInfoEndpoint.oidcUserService(localAuthoritiesOidcUserService)))
				.oidcLogout(oidcLogout -> oidcLogout.backChannel(
						backChannel -> backChannel.logoutHandler(new SessionRepositoryOidcBackChannelLogoutHandler(
								oidcSessionRegistry, sessionRepository, sessionLifecycleAuditLogger))))
				.logout(logout -> logout
					.addLogoutHandler(new SessionLifecycleLogoutHandler(sessionLifecycleAuditLogger))
					.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
				.with(new DefaultLoginPageConfigurer<>(),
						defaultLoginPage -> defaultLoginPage.withObjectPostProcessor(new ObjectPostProcessor<Object>() {
							@Override
							public <O> O postProcess(O object) {
								if (object instanceof DefaultLoginPageGeneratingFilter filter) {
									// Show the logout message after the post-logout
									// redirect.
									filter.setLogoutSuccessUrl(LOGOUT_SUCCESS_URI);
								}
								return object;
							}
						}));
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
	 * Gets the entry point a browser is redirected to when it is not authenticated: the
	 * authorization request URI of the only client registration, exactly as Spring
	 * Security's own default entry point would choose, or the login page when there are
	 * several.
	 * @param clientRegistrationRepository the client registrations
	 * @return the browser login entry point URI
	 */
	static String authorizationRequestUri(ClientRegistrationRepository clientRegistrationRepository) {
		List<String> registrationIds = new ArrayList<>();
		if (clientRegistrationRepository instanceof Iterable<?> registrations) {
			for (Object registration : registrations) {
				registrationIds.add(((ClientRegistration) registration).getRegistrationId());
			}
		}
		if (registrationIds.size() == 1) {
			return OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/"
					+ registrationIds.get(0);
		}
		return LOGIN_PAGE_URI;
	}

	private static LocalAuthorityLookup requireLocalAuthorityLookup(
			ObjectProvider<LocalAuthorityLookup> localAuthorityLookup) {
		return localAuthorityLookup.getIfAvailable(() -> {
			throw new IllegalStateException(
					"The commons security configuration requires a LocalAuthorityLookup bean that returns "
							+ "the application's local authorities for a username; define one, for example backed by the "
							+ "application's user repository (see docs/adr/0005 and docs/adr/0019)");
		});
	}

	private static JWKSource<SecurityContext> jwkSource(ClientRegistration clientRegistration) {
		String jwkSetUri = clientRegistration.getProviderDetails().getJwkSetUri();
		try {
			return JWKSourceBuilder.create(URI.create(jwkSetUri).toURL()).retrying(true).build();
		}
		catch (MalformedURLException | IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid JWK Set URI for client registration "
					+ clientRegistration.getRegistrationId() + ": " + jwkSetUri, ex);
		}
	}

	/**
	 * Gets the OIDC logout success handler that calls the OpenID Provider's
	 * {@code end_session_endpoint}.
	 * @param clientRegistrationRepository the client registrations
	 * @return the logout success handler
	 */
	private static LogoutSuccessHandler oidcLogoutSuccessHandler(
			ClientRegistrationRepository clientRegistrationRepository) {
		OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(
				clientRegistrationRepository);
		oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}" + LOGOUT_SUCCESS_URI);
		return oidcLogoutSuccessHandler;
	}

}
