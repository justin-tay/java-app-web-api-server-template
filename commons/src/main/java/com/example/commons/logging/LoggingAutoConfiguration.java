package com.example.commons.logging;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.HeaderWriterFilter;

import com.example.commons.logging.client.ClientIpResolver;
import com.example.commons.logging.request.RequestIdResolver;

/**
 * Configures request-scoped structured logging: MDC correlation and cleanup around every
 * request, and the authenticated user and request lifecycle events inside every Spring
 * Security filter chain.
 *
 * <p>
 * Applied whenever commons is on the classpath of a servlet application. Set
 * {@code commons.logging.enabled=false} to turn it off. The ECS log format and trace
 * correlation fields are configured separately, through the defaults that
 * {@code CommonsDefaultsEnvironmentPostProcessor} contributes.
 */
@AutoConfiguration(before = TaskExecutionAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "commons.logging.enabled", matchIfMissing = true)
public class LoggingAutoConfiguration {

	/**
	 * Order of the logging {@code Customizer<HttpSecurity>}: ahead of the other commons
	 * customizers, which position their filters next to the ones it adds.
	 */
	public static final int FILTER_CHAIN_CUSTOMIZER_ORDER = 0;

	/**
	 * Query parameters whose values are replaced with {@code [REDACTED]} in logged
	 * {@code url.query} values (matched case-insensitively). Besides the OAuth 2.0 and
	 * OIDC credentials and correlation values, this covers the names a client could use
	 * to send a session ID: {@code id} (the session cookie name,
	 * {@code server.servlet.session.cookie.name}, set by each application),
	 * {@code jsessionid} (the servlet container default), and {@code session} and
	 * {@code sessionid} (Spring Session's default cookie name and a common variant). The
	 * session ID is only ever accepted from the {@code id} cookie, but a value sent this
	 * way must still not reach the logs.
	 */
	static final List<String> QUERY_PARAMETER_REDACT_LIST = List.of("access_token", "client_assertion", "client_secret",
			"code", "code_verifier", "id", "id_token", "id_token_hint", "jsessionid", "logout_token", "refresh_token",
			"session", "session_state", "sessionid", "state");

	/**
	 * Supplies no end-user client IP by default. Define a {@link ClientIpResolver} bean
	 * to replace it with a trusted resolver when the deployment ingress provides one.
	 * @return the safe default client IP resolver
	 */
	@Bean
	@ConditionalOnMissingBean
	ClientIpResolver clientIpResolver() {
		return ClientIpResolver.none();
	}

	/**
	 * Supplies no upstream request ID by default. Define a {@link RequestIdResolver} bean
	 * to replace it with an ingress-specific resolver when the deployment provides one.
	 * @return the safe default request ID resolver
	 */
	@Bean
	@ConditionalOnMissingBean
	RequestIdResolver requestIdResolver() {
		return RequestIdResolver.none();
	}

	/**
	 * Propagates the request's MDC to tasks run by Spring Boot's auto-configured task
	 * executor, which applies a single {@link TaskDecorator} bean. Define a
	 * {@code TaskDecorator} bean to replace it.
	 * @return the MDC-propagating task decorator
	 */
	@Bean
	@ConditionalOnMissingBean(TaskDecorator.class)
	MdcTaskDecorator mdcTaskDecorator() {
		return new MdcTaskDecorator();
	}

	@Bean
	LoggingContextCleanupFilter loggingContextCleanupFilter() {
		return new LoggingContextCleanupFilter();
	}

	/**
	 * Registers {@link LoggingContextCleanupFilter} as the outermost filter (the lowest
	 * order of any filter in the application), so its MDC cleanup is the last thing that
	 * runs before control returns to the servlet container, regardless of what any inner
	 * filter or library left behind. See docs/adr/0012.
	 * @param filter the filter to register
	 * @return the registration
	 */
	@Bean
	FilterRegistrationBean<LoggingContextCleanupFilter> loggingContextCleanupFilterRegistration(
			LoggingContextCleanupFilter filter) {
		FilterRegistrationBean<LoggingContextCleanupFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
		return registration;
	}

	@Bean
	RequestCorrelationContextFilter requestCorrelationContextFilter(ClientIpResolver clientIpResolver,
			RequestIdResolver requestIdResolver) {
		return new RequestCorrelationContextFilter(clientIpResolver, requestIdResolver);
	}

	/**
	 * Registers {@link RequestCorrelationContextFilter} ahead of Spring Security's own
	 * filter chain (order {@code -100}; see
	 * {@code SecurityProperties.DEFAULT_FILTER_ORDER}), so
	 * {@code http.request.id}/{@code source.ip}/{@code client.ip} are present even when
	 * the {@code HttpFirewall} rejects a request before Spring Security's internal filter
	 * list is ever invoked. See docs/adr/0012.
	 * @param filter the filter to register
	 * @return the registration
	 */
	@Bean
	FilterRegistrationBean<RequestCorrelationContextFilter> requestCorrelationContextFilterRegistration(
			RequestCorrelationContextFilter filter) {
		FilterRegistrationBean<RequestCorrelationContextFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
		return registration;
	}

	/**
	 * Adds the logging filters to every Spring Security filter chain the application
	 * builds.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpSecurity.class)
	static class SecurityFilterChainLoggingConfiguration {

		@Bean
		AuthenticatedUserLoggingContextFilter authenticatedUserLoggingContextFilter() {
			return new AuthenticatedUserLoggingContextFilter();
		}

		/**
		 * Spring Security applies every {@code Customizer<HttpSecurity>} bean to each
		 * {@code HttpSecurity} before the application's own {@code SecurityFilterChain}
		 * bean method configures it, so an application's filters can be positioned
		 * relative to the filters added here.
		 *
		 * <p>
		 * {@link RequestLoggingFilter} is placed immediately before
		 * {@link HeaderWriterFilter} rather than immediately after
		 * {@link SecurityContextHolderFilter}, so that session-handling filters an
		 * application adds after {@code SecurityContextHolderFilter}, such as an absolute
		 * session timeout, run first and the logged user reflects their outcome.
		 * @param authenticatedUserLoggingContextFilter the authenticated user MDC filter
		 * @return the customizer
		 */
		@Bean
		@Order(FILTER_CHAIN_CUSTOMIZER_ORDER)
		Customizer<HttpSecurity> loggingFilterChainCustomizer(
				AuthenticatedUserLoggingContextFilter authenticatedUserLoggingContextFilter) {
			return http -> http
				.addFilterBefore(authenticatedUserLoggingContextFilter, SecurityContextHolderFilter.class)
				.addFilterBefore(new RequestLoggingFilter(QUERY_PARAMETER_REDACT_LIST), HeaderWriterFilter.class);
		}

	}

}
