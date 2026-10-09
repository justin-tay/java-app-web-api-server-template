package com.example.commons.web.forwarded;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.autoconfigure.servlet.ForwardedHeaderFilterCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Registers the {@code ForwardedHeaderFilter} for
 * {@code server.forward-headers-strategy=framework} after
 * {@code RequestCorrelationContextFilter} instead of before it, where Spring Boot puts
 * it. The filter hides {@code X-Forwarded-For} and replaces
 * {@code HttpServletRequest.getRemoteAddr()} with the forwarded address, and the
 * correlation filter needs both unchanged to resolve {@code client.ip} and record
 * {@code source.ip}. Otherwise registered exactly as Spring Boot registers its own, which
 * backs off because this bean is a {@code FilterRegistrationBean<ForwardedHeaderFilter>}.
 */
@AutoConfiguration(before = TomcatServletWebServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "server.forward-headers-strategy", havingValue = "framework")
public class ForwardedHeadersAutoConfiguration {

	/**
	 * Runs after the correlation filter, which is registered at
	 * {@code HIGHEST_PRECEDENCE + 10}.
	 */
	static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

	@Bean
	FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter(
			ObjectProvider<ForwardedHeaderFilterCustomizer> customizerProvider) {
		ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
		customizerProvider.ifAvailable(customizer -> customizer.customize(filter));
		FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
		registration.setOrder(ORDER);
		return registration;
	}

}
