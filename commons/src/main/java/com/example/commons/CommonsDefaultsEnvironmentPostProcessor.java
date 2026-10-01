package com.example.commons;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

import com.example.commons.security.oauth2.ConditionalOnIssuerUriClientRegistration.OnIssuerUriClientRegistrationCondition;
import com.example.commons.security.oauth2.ConditionalOnPrivateKeyJwtClientRegistration.OnPrivateKeyJwtClientRegistrationCondition;

/**
 * Contributes the commons configuration defaults in {@value #DEFAULTS_LOCATION}, such as
 * the ECS log format, the TLS protocols and cipher suites, and the actuator management
 * port, so every application starts from the same secure baseline.
 *
 * <p>
 * The defaults have lower precedence than every application configuration source,
 * including {@code application.yaml}, profile-specific files, environment variables, and
 * command-line arguments, so an application overrides any single key by setting it. Only
 * {@link SpringApplication#setDefaultProperties(java.util.Map) SpringApplication default
 * properties} rank lower.
 */
public class CommonsDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String DEFAULTS_LOCATION = "META-INF/commons-defaults.yaml";

	static final String PROPERTY_SOURCE_NAME = "commonsDefaults";

	/**
	 * The readiness group adds the {@code jwks} health contributor when a client
	 * registration uses {@code private_key_jwt} (see docs/adr/0020) and the
	 * {@code oidcDiscovery} one when a client provider has an {@code issuer-uri} (see
	 * docs/adr/0029), as those only exist then.
	 */
	static final String READINESS_PROPERTY_SOURCE_NAME = "commonsReadinessDefaults";

	@Override
	public int getOrder() {
		// Run after ConfigDataEnvironmentPostProcessor has added the application's own
		// configuration files, so these defaults can be placed below them.
		return Ordered.LOWEST_PRECEDENCE;
	}

	private static final String OAUTH2_CLIENT_PROPERTIES_CLASS = "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		MutablePropertySources propertySources = environment.getPropertySources();
		if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
			return;
		}
		for (PropertySource<?> defaults : load()) {
			if (propertySources.contains(DefaultPropertiesPropertySource.NAME)) {
				propertySources.addBefore(DefaultPropertiesPropertySource.NAME, defaults);
			}
			else {
				propertySources.addLast(defaults);
			}
		}
		// Only a private_key_jwt application has the jwks health contributor, and only an
		// application with an issuer-uri has the oidcDiscovery one. Naming a missing
		// contributor in a health group fails startup, so each is added only then, just
		// above the other defaults. Without the OAuth2 client on the classpath there is
		// no client registration to check.
		if (propertySources.contains(PROPERTY_SOURCE_NAME)
				&& ClassUtils.isPresent(OAUTH2_CLIENT_PROPERTIES_CLASS, getClass().getClassLoader())) {
			StringBuilder include = new StringBuilder("readinessState");
			if (OnPrivateKeyJwtClientRegistrationCondition.matches(environment)) {
				include.append(",jwks");
			}
			if (OnIssuerUriClientRegistrationCondition.matches(environment)) {
				include.append(",oidcDiscovery");
			}
			if (include.indexOf(",") >= 0) {
				propertySources.addBefore(PROPERTY_SOURCE_NAME, new MapPropertySource(READINESS_PROPERTY_SOURCE_NAME,
						Map.of("management.endpoint.health.group.readiness.include", include.toString())));
			}
		}
	}

	private List<PropertySource<?>> load() {
		Resource resource = new ClassPathResource(DEFAULTS_LOCATION, getClass().getClassLoader());
		try {
			return new YamlPropertySourceLoader().load(PROPERTY_SOURCE_NAME, resource);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Unable to load commons defaults from " + DEFAULTS_LOCATION, ex);
		}
	}

}
