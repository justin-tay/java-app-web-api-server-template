package com.example.commons.aws.secretsmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

class SecretsManagerResourceTest {

	private final SecretsManagerClient client = mock(SecretsManagerClient.class);

	private final SecretsManagerResource resource = new SecretsManagerResource("app/jwks", () -> this.client);

	@Test
	@SuppressWarnings("unchecked")
	void readsTheSecretStringOfTheCurrentVersion() throws IOException {
		given(this.client.getSecretValue(any(Consumer.class)))
			.willReturn(GetSecretValueResponse.builder().secretString("{\"keys\":[]}").build());

		assertThat(read()).isEqualTo("{\"keys\":[]}");

		ArgumentCaptor<Consumer<GetSecretValueRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
		verify(this.client).getSecretValue(request.capture());
		GetSecretValueRequest.Builder builder = GetSecretValueRequest.builder();
		request.getValue().accept(builder);
		assertThat(builder.build().secretId()).isEqualTo("app/jwks");
		assertThat(builder.build().versionStage()).isEqualTo("AWSCURRENT");
	}

	@Test
	@SuppressWarnings("unchecked")
	void readsTheSecretBinaryWhenThereIsNoString() throws IOException {
		given(this.client.getSecretValue(any(Consumer.class))).willReturn(GetSecretValueResponse.builder()
			.secretBinary(SdkBytes.fromString("binary", StandardCharsets.UTF_8))
			.build());

		assertThat(read()).isEqualTo("binary");
	}

	@Test
	@SuppressWarnings("unchecked")
	void readsTheSecretAgainOnEveryRead() throws IOException {
		given(this.client.getSecretValue(any(Consumer.class))).willReturn(
				GetSecretValueResponse.builder().secretString("first").build(),
				GetSecretValueResponse.builder().secretString("second").build());

		assertThat(read()).isEqualTo("first");
		assertThat(read()).isEqualTo("second");
	}

	@Test
	@SuppressWarnings("unchecked")
	void aMissingSecretIsAFileNotFoundException() {
		given(this.client.getSecretValue(any(Consumer.class)))
			.willThrow(ResourceNotFoundException.builder().message("not found").build());

		assertThatExceptionOfType(FileNotFoundException.class).isThrownBy(this.resource::getInputStream)
			.withMessageContaining("app/jwks");
		assertThat(this.resource.exists()).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	void anSdkFailureIsAnIoException() {
		given(this.client.getSecretValue(any(Consumer.class)))
			.willThrow(SdkClientException.create("Unable to connect"));

		assertThatExceptionOfType(IOException.class).isThrownBy(this.resource::getInputStream)
			.withMessageContaining("app/jwks")
			.withCauseInstanceOf(SdkClientException.class);
	}

	@Test
	void describesTheSecret() {
		assertThat(this.resource.getDescription()).isEqualTo("AWS Secrets Manager secret [app/jwks]");
		assertThat(this.resource).isEqualTo(new SecretsManagerResource("app/jwks", () -> this.client))
			.isNotEqualTo(new SecretsManagerResource("other", () -> this.client));
	}

	private String read() throws IOException {
		try (InputStream inputStream = this.resource.getInputStream()) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
