package com.example.commons.aws.secretsmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Reads a secret through the {@code aws-secretsmanager:} protocol and Spring Cloud AWS's
 * auto-configured {@link SecretsManagerClient}, against Secrets Manager in LocalStack.
 * Skipped when Docker is not available.
 */
@SpringBootTest(classes = SecretsManagerResourceIntegrationTest.TestApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class SecretsManagerResourceIntegrationTest {

	@Container
	@ServiceConnection
	static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4"));

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

	}

}
