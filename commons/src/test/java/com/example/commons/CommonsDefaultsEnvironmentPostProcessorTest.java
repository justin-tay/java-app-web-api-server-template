package com.example.commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class CommonsDefaultsEnvironmentPostProcessorTest {

	private final CommonsDefaultsEnvironmentPostProcessor postProcessor = new CommonsDefaultsEnvironmentPostProcessor();

	private final StandardEnvironment environment = new StandardEnvironment();

	@Test
	void contributesTheDefaults() {
		this.postProcessor.postProcessEnvironment(this.environment, new SpringApplication());

		assertThat(this.environment.getProperty("management.server.port")).isEqualTo("8082");
		assertThat(this.environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
		assertThat(this.environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
		assertThat(this.environment.getProperty("logging.structured.json.customizer[0]"))
			.isEqualTo("com.example.commons.logging.TraceCorrelationJsonMembersCustomizer");
		assertThat(this.environment.getProperty("server.ssl.enabled-protocols[0]")).isEqualTo("TLSv1.2");
	}

	@Test
	void ranksBelowApplicationConfiguration() {
		this.environment.getPropertySources()
			.addLast(new MapPropertySource("applicationConfig", Map.of("management.server.port", "9000")));

		this.postProcessor.postProcessEnvironment(this.environment, new SpringApplication());

		assertThat(this.environment.getProperty("management.server.port")).isEqualTo("9000");
		assertThat(this.environment.getPropertySources()
			.stream()
			.reduce((first, second) -> second)
			.orElseThrow()
			.getName()).isEqualTo(CommonsDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
	}

	@Test
	void ranksAboveSpringApplicationDefaultProperties() {
		this.environment.getPropertySources()
			.addLast(new DefaultPropertiesPropertySource(Map.of("management.server.port", "9000")));

		this.postProcessor.postProcessEnvironment(this.environment, new SpringApplication());

		assertThat(this.environment.getProperty("management.server.port")).isEqualTo("8082");
	}

	@Test
	void addsTheDefaultsOnce() {
		this.postProcessor.postProcessEnvironment(this.environment, new SpringApplication());
		this.postProcessor.postProcessEnvironment(this.environment, new SpringApplication());

		assertThat(this.environment.getPropertySources()
			.stream()
			.filter(source -> source.getName().equals(CommonsDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)))
			.hasSize(1);
	}

}
