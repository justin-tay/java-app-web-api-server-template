package com.example.commons.security;

import java.time.Clock;

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
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.session.SessionRegistry;
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
import com.example.commons.security.authorization.ProblemDetailAccessDeniedHandler;
import com.example.commons.security.firewall.ProblemDetailRequestRejectedHandler;
import com.example.commons.security.session.AbsoluteSessionTimeoutFilter;
import com.example.commons.security.session.AuditingInvalidSessionStrategy;
import com.example.commons.security.session.ContentNegotiatingSessionExpiredStrategy;
import com.example.commons.security.session.SessionLifecycleAuditInitializationFilter;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionLifecycleLogoutHandler;
import com.example.commons.security.session.SecureRandomSessionIdGenerator;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Configures the security baseline every application's Spring Security filter chain
 * shares: security response headers, RFC 9457 Problem Details for authentication,
 * authorization, session, and firewall failures, JDBC-backed session management with an
 * absolute timeout and a single session per user, session lifecycle and security audit
 * logging, and method security. OIDC login is added by
 * {@link OidcLoginSecurityAutoConfiguration}.
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
 * {@code commons.security.enabled=false} to turn it off.
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class,
		after = { LoggingAutoConfiguration.class, OidcLoginSecurityAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ HttpSecurity.class, JdbcIndexedSessionRepository.class })
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

	static final String LOGIN_PAGE_URI = "/login";

	static final String LOGOUT_SUCCESS_URI = LOGIN_PAGE_URI + "?logout";

	/**
	 * Provides the entry point for unauthenticated requests when no OIDC login is
	 * configured: a browser is redirected to the login page, and a non-browser client
	 * receives an RFC 9457 Problem Details response.
	 * @return the entry point
	 */
	@Bean
	@ConditionalOnMissingBean
	ProblemDetailAuthenticationEntryPoint authenticationEntryPoint() {
		return new ProblemDetailAuthenticationEntryPoint(LOGIN_PAGE_URI);
	}

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

	/**
	 * Applies the security baseline to every {@code HttpSecurity}.
	 *
	 * <p>
	 * The filters are positioned relative to Spring Security's own filters, keeping this
	 * order: {@code AuthenticatedUserLoggingContextFilter} (commons logging),
	 * {@link SessionLifecycleAuditInitializationFilter},
	 * {@link SecurityContextHolderFilter}, {@link AbsoluteSessionTimeoutFilter},
	 * {@code RequestLoggingFilter} (commons logging), {@link HeaderWriterFilter}.
	 * {@link OidcLoginSecurityAutoConfiguration} adds {@code LocalAuthorityRefreshFilter}
	 * just before {@link HeaderWriterFilter}. Filters that share a position keep the
	 * order they were added in, which is why this customizer runs after the logging one.
	 * @return the customizer
	 */
	@Bean
	@Order(FILTER_CHAIN_CUSTOMIZER_ORDER)
	Customizer<HttpSecurity> securityFilterChainCustomizer(WebSecurityProperties properties,
			ObjectProvider<Clock> clock, ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			AuthenticationEventPublisher authenticationEventPublisher, SessionRegistry sessionRegistry,
			SessionInformationExpiredStrategy sessionExpiredStrategy,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger, Environment environment) {
		String healthPath = environment.getProperty("management.endpoints.web.base-path", "/actuator") + "/health";
		return http -> {
			http.getSharedObject(AuthenticationManagerBuilder.class)
				.authenticationEventPublisher(authenticationEventPublisher);
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
				.logout(logout -> logout
					.addLogoutHandler(new SessionLifecycleLogoutHandler(sessionLifecycleAuditLogger)));
		};
	}

}
