package com.example.commons.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Tests that {@code private_key_jwt} requires an explicitly configured private JWKS. See
 * docs/adr/0018.
 */
class JwksPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
				ValidationAutoConfiguration.class))
		.withUserConfiguration(JwksPropertiesConfiguration.class);

	@Test
	void startupFailsWithAClearMessageWhenTheJwksLocationIsNotSet() {
		this.contextRunner.run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.hasMessageContaining("commons.security.oauth2.jwks")
			.hasMessageContaining(JwksProperties.JWKS_REQUIRED_MESSAGE));
	}

	@Test
	void startupFailsWhenTheJwksLocationIsBlank() {
		this.contextRunner.withPropertyValues("commons.security.oauth2.jwks= ")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining(JwksProperties.JWKS_REQUIRED_MESSAGE));
	}

	@Test
	void startsWhenTheJwksLocationIsSet() {
		this.contextRunner.withPropertyValues("commons.security.oauth2.jwks=file:/run/secrets/jwks.json")
			.run(context -> assertThat(context).hasNotFailed()
				.getBean(JwksProperties.class)
				.extracting(JwksProperties::getJwks)
				.isEqualTo("file:/run/secrets/jwks.json"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(JwksProperties.class)
	static class JwksPropertiesConfiguration {

	}

}
