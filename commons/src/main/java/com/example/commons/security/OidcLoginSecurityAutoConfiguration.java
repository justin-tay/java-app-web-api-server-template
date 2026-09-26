package com.example.commons.security;

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
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import com.example.commons.security.authentication.ProblemDetailAuthenticationEntryPoint;
import com.example.commons.security.authentication.oidc.LocalAuthoritiesOidcUserService;
import com.example.commons.security.authentication.oidc.MaxAgeAuthorizationRequestResolver;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.authorization.LocalAuthorityRefreshFilter;
import com.example.commons.security.oauth2.IdTokenDecryption;
import com.example.commons.security.oauth2.OidcIdTokenDecoders;
import com.example.commons.security.session.JdbcOidcSessionRegistry;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionRepositoryOidcBackChannelLogoutHandler;

/**
 * Configures OpenID Connect login for the application's Spring Security filter chain:
 * OAuth2 login with locally managed authorities, ID token decoding, OIDC back-channel and
 * RP-initiated logout, and the entry point that redirects a browser to the authorization
 * endpoint.
 *
 * <p>
 * Applied when {@code spring-security-oauth2-client} is on the classpath of a servlet
 * application and commons security is enabled. Every application must define a
 * {@link LocalAuthorityLookup} bean. Without the OAuth2 client,
 * {@link WebSecurityAutoConfiguration} still applies the rest of the security baseline
 * and leaves authentication to the application.
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class,
		afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ HttpSecurity.class, ClientRegistrationRepository.class })
@ConditionalOnBooleanProperty(name = "commons.security.enabled", matchIfMissing = true)
public class OidcLoginSecurityAutoConfiguration {

	/**
	 * Order of {@link #oidcLoginFilterChainCustomizer}: after the core security
	 * customizer, whose session management and logout handling it extends.
	 */
	public static final int FILTER_CHAIN_CUSTOMIZER_ORDER = WebSecurityAutoConfiguration.FILTER_CHAIN_CUSTOMIZER_ORDER
			+ 100;

	/**
	 * Provides the entry point for unauthenticated requests: a browser is redirected to
	 * the authorization endpoint of the only client registration, or to the login page
	 * when there are several, and a non-browser client receives an RFC 9457 Problem
	 * Details response.
	 * @param clientRegistrationRepository the client registrations
	 * @return the entry point
	 */
	@Bean
	@ConditionalOnMissingBean
	ProblemDetailAuthenticationEntryPoint oidcAuthenticationEntryPoint(
			ClientRegistrationRepository clientRegistrationRepository) {
		return new ProblemDetailAuthenticationEntryPoint(authorizationRequestUri(clientRegistrationRepository));
	}

	/**
	 * Provides the registry that links an OpenID Provider session (its {@code sid} and
	 * {@code sub}) to the local session created at login, so a back-channel logout token
	 * can be resolved to the local session it ends. It is held in the database next to
	 * the sessions, so a token that reaches any instance resolves a session that logged
	 * in through any other.
	 * @param jdbcClient the JDBC client
	 * @param clock the clock, if the application defines one
	 * @param environment the environment, for the Spring Session table name
	 * @return the JDBC OIDC session registry
	 */
	@Bean
	@ConditionalOnMissingBean
	OidcSessionRegistry oidcSessionRegistry(JdbcClient jdbcClient, ObjectProvider<Clock> clock,
			Environment environment) {
		return new JdbcOidcSessionRegistry(jdbcClient, clock.getIfAvailable(Clock::systemUTC),
				environment.getProperty("spring.session.jdbc.table-name", "SPRING_SESSION"));
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
		return clientRegistration -> jwtDecoders.computeIfAbsent(clientRegistration.getRegistrationId(),
				key -> OidcIdTokenDecoders.create(clientRegistration, idTokenDecryption.getIfAvailable()));
	}

	@Bean
	LocalAuthoritiesOidcUserService localAuthoritiesOidcUserService(
			ObjectProvider<LocalAuthorityLookup> localAuthorityLookup) {
		return new LocalAuthoritiesOidcUserService(requireLocalAuthorityLookup(localAuthorityLookup));
	}

	/**
	 * Adds OAuth2 login, OIDC back-channel logout and RP-initiated logout to every
	 * {@code HttpSecurity}.
	 * @param clientRegistrationRepository the client registrations
	 * @param localAuthoritiesOidcUserService the OIDC user service
	 * @param oidcSessionRegistry the registry linking OpenID Provider sessions to local
	 * sessions
	 * @param sessionRepository the Spring Session repository
	 * @param sessionLifecycleAuditLogger the session lifecycle audit logger
	 * @param sessionRegistry the session registry
	 * @param localAuthorityLookup the local authority lookup
	 * @return the customizer
	 */
	@Bean
	@Order(FILTER_CHAIN_CUSTOMIZER_ORDER)
	Customizer<HttpSecurity> oidcLoginFilterChainCustomizer(ClientRegistrationRepository clientRegistrationRepository,
			LocalAuthoritiesOidcUserService localAuthoritiesOidcUserService, OidcSessionRegistry oidcSessionRegistry,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger, SessionRegistry sessionRegistry,
			ObjectProvider<LocalAuthorityLookup> localAuthorityLookup) {
		LocalAuthorityLookup lookup = requireLocalAuthorityLookup(localAuthorityLookup);
		return http -> http
			.addFilterBefore(new LocalAuthorityRefreshFilter(lookup, sessionLifecycleAuditLogger, sessionRegistry),
					HeaderWriterFilter.class)
			.oauth2Login(oauth2Login -> oauth2Login
				.authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
					.authorizationRequestResolver(new MaxAgeAuthorizationRequestResolver(clientRegistrationRepository)))
				.userInfoEndpoint(
						userInfoEndpoint -> userInfoEndpoint.oidcUserService(localAuthoritiesOidcUserService)))
			.oidcLogout(oidcLogout -> oidcLogout
				.backChannel(backChannel -> backChannel.logoutHandler(new SessionRepositoryOidcBackChannelLogoutHandler(
						oidcSessionRegistry, sessionRepository, sessionLifecycleAuditLogger))))
			.logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
			.with(new DefaultLoginPageConfigurer<>(),
					defaultLoginPage -> defaultLoginPage.withObjectPostProcessor(new ObjectPostProcessor<Object>() {
						@Override
						public <O> O postProcess(O object) {
							if (object instanceof DefaultLoginPageGeneratingFilter filter) {
								// Show the logout message after the post-logout
								// redirect.
								filter.setLogoutSuccessUrl(LoginPaths.LOGOUT_SUCCESS_URI);
							}
							return object;
						}
					}));
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
		return LoginPaths.LOGIN_PAGE_URI;
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
		oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}" + LoginPaths.LOGOUT_SUCCESS_URI);
		return oidcLogoutSuccessHandler;
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

}
