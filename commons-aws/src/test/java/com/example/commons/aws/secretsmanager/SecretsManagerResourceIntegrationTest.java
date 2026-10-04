package com.example.commons.aws.secretsmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.floci.testcontainers.FlociContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Reads a secret through the {@code aws-secretsmanager:} protocol and a
 * {@link SecretsManagerClient} pointed at Secrets Manager in
 * <a href="https://github.com/floci-io/floci">Floci</a>, an MIT-licensed AWS emulator
 * that needs no account or auth token. Floci runs Secrets Manager in process, so its
 * container gets no Docker socket. Skipped when Docker is not available.
 */
@SpringBootTest(classes = SecretsManagerResourceIntegrationTest.TestApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class SecretsManagerResourceIntegrationTest {

	@Container
	static FlociContainer floci = new FlociContainer(DockerImageName.parse("floci/floci:2.1.0"))
		.withDockerSocket(false);

	@Autowired
	private ApplicationContext context;

	@Autowired
	private SecretsManagerClient client;

	@Test
	void readsTheCurrentVersionByNameOrArn() throws Exception {
		String arn = this.client.createSecret(request -> request.name("jwks").secretString("{\"keys\":[]}")).arn();
		this.client.putSecretValue(request -> request.secretId(arn).secretString("{\"keys\":[1]}"));

		assertThat(read("aws-secretsmanager:" + arn)).isEqualTo("{\"keys\":[1]}");
		assertThat(read("aws-secretsmanager:jwks")).isEqualTo("{\"keys\":[1]}");
	}

	private String read(String location) throws Exception {
		try (InputStream inputStream = this.context.getResource(location).getInputStream()) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		SecretsManagerClient secretsManagerClient() {
			return SecretsManagerClient.builder()
				.endpointOverride(URI.create(floci.getEndpoint()))
				.region(Region.of(floci.getRegion()))
				.credentialsProvider(StaticCredentialsProvider
					.create(AwsBasicCredentials.create(floci.getAccessKey(), floci.getSecretKey())))
				.build();
		}

	}

}
