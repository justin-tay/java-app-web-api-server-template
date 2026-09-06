package com.example.app.web.server.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

/**
 * Enables Tomcat strict Servlet compliance before Spring creates the embedded server.
 *
 * <p>
 * This initializer is registered through {@code META-INF/spring.factories} so the
 * Tomcat-specific system property is applied independently of the application's entry
 * point.
 */
public final class TomcatApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		System.setProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE", "true");
	}

}
