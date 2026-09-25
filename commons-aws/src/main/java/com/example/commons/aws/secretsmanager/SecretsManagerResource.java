package com.example.commons.aws.secretsmanager;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.springframework.core.io.AbstractResource;
import org.springframework.util.Assert;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * A {@link org.springframework.core.io.Resource} whose content is the {@code AWSCURRENT}
 * version of an AWS Secrets Manager secret: its {@code SecretString}, or its
 * {@code SecretBinary} when the secret has no string.
 *
 * <p>
 * Every {@link #getInputStream()} fetches the secret again, so a caller that re-reads the
 * resource sees the secret's current version after a rotation. Caching, if any, is the
 * caller's decision.
 */
public class SecretsManagerResource extends AbstractResource {

	/**
	 * The staging label of the version every read returns.
	 */
	static final String VERSION_STAGE = "AWSCURRENT";

	private final String secretId;

	private final Supplier<SecretsManagerClient> client;

	/**
	 * Creates a resource for a secret.
	 * @param secretId the secret's name or ARN
	 * @param client supplies the client the secret is read with, looked up on each read
	 */
	public SecretsManagerResource(String secretId, Supplier<SecretsManagerClient> client) {
		Assert.hasText(secretId, "secretId must not be empty");
		Assert.notNull(client, "client must not be null");
		this.secretId = secretId;
		this.client = client;
	}

	/**
	 * Returns the secret's name or ARN.
	 * @return the secret ID
	 */
	public String getSecretId() {
		return this.secretId;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		GetSecretValueResponse response;
		try {
			response = this.client.get()
				.getSecretValue(request -> request.secretId(this.secretId).versionStage(VERSION_STAGE));
		}
		catch (ResourceNotFoundException ex) {
			throw new FileNotFoundException(getDescription() + " does not exist");
		}
		catch (SdkException ex) {
			throw new IOException("Unable to read " + getDescription(), ex);
		}
		if (response.secretString() != null) {
			return new ByteArrayInputStream(response.secretString().getBytes(StandardCharsets.UTF_8));
		}
		if (response.secretBinary() != null) {
			return response.secretBinary().asInputStream();
		}
		throw new FileNotFoundException(getDescription() + " has no " + VERSION_STAGE + " value");
	}

	@Override
	public String getDescription() {
		return "AWS Secrets Manager secret [" + this.secretId + "]";
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof SecretsManagerResource that && this.secretId.equals(that.secretId));
	}

	@Override
	public int hashCode() {
		return this.secretId.hashCode();
	}

}
