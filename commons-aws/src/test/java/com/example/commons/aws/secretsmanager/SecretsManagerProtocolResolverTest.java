package com.example.commons.aws.secretsmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class SecretsManagerProtocolResolverTest {

	private static final String ARN = "arn:aws:secretsmanager:ap-southeast-1:123456789012:secret:jwks-AbCdEf";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SecretsManagerResourceAutoConfiguration.class));

	@Test
	void resolvesASecretLocationThroughTheApplicationContext() {
		this.contextRunner.withUserConfiguration(ClientConfiguration.class).run(context -> {
			Resource resource = context.getResource("aws-secretsmanager:" + ARN);

			assertThat(resource).isInstanceOf(SecretsManagerResource.class);
			assertThat(((SecretsManagerResource) resource).getSecretId()).isEqualTo(ARN);
			try (InputStream inputStream = resource.getInputStream()) {
				assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"keys\":[]}");
			}
		});
	}

	@Test
	void leavesOtherLocationsToTheDefaultResolution() {
		this.contextRunner.withUserConfiguration(ClientConfiguration.class)
			.run(context -> assertThat(context.getResource("classpath:jwks.json"))
				.isInstanceOf(ClassPathResource.class));
	}

	@Test
	void readingASecretWithoutAClientSaysSo() {
		SecretsManagerProtocolResolver resolver = new SecretsManagerProtocolResolver();
		Resource resource = resolver.resolve("aws-secretsmanager:jwks", null);

		assertThatIllegalStateException().isThrownBy(resource::getInputStream)
			.withMessage(SecretsManagerProtocolResolver.CLIENT_UNAVAILABLE_MESSAGE);
	}

	@Test
	void theDefaultClientIsOnlyBuiltWhenASecretIsRead() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeanFactory().containsBeanDefinition("secretsManagerClient")).isTrue();
			assertThat(context.getBeanFactory().getSingletonNames()).doesNotContain("secretsManagerClient");
		});
	}

	@Test
	void theDefaultClientBacksOffForAnApplicationClient() {
		this.contextRunner.withUserConfiguration(ClientConfiguration.class)
			.run(context -> assertThat(context).getBeans(SecretsManagerClient.class).hasSize(1));
	}

	@Configuration(proxyBeanMethods = false)
	static class ClientConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		SecretsManagerClient secretsManagerClient() {
			SecretsManagerClient client = mock(SecretsManagerClient.class);
			given(client.getSecretValue(any(Consumer.class)))
				.willReturn(GetSecretValueResponse.builder().secretString("{\"keys\":[]}").build());
			return client;
		}

	}

}
