package com.example.commons;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

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

	@Override
	public int getOrder() {
		// Run after ConfigDataEnvironmentPostProcessor has added the application's own
		// configuration files, so these defaults can be placed below them.
		return Ordered.LOWEST_PRECEDENCE;
	}

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
