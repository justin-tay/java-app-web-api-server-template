package com.example.commons.security.oauth2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties for {@code private_key_jwt} client authentication, bound only when a client
 * registration uses it.
 */
@ConfigurationProperties(prefix = "commons.security.oauth2")
@Validated
public class JwksProperties {

	/**
	 * Message reported when {@code commons.security.oauth2.jwks} is unset, which fails
	 * startup.
	 */
	static final String JWKS_REQUIRED_MESSAGE = "must list the locations of this deployment's private JWKS "
			+ "(for example file:/run/secrets/jwks.json or aws-secretsmanager:<secret name or ARN>) "
			+ "because a client registration uses private_key_jwt; "
			+ "no JWKS is packaged with the application, see docs/adr/0018";

	/**
	 * Resource locations of the private JWKS used for {@code private_key_jwt} client
	 * authentication ({@code sig} keys) and, optionally, ID token decryption ({@code enc}
	 * keys). Each location holds the keys of one use, or both uses when one location is
	 * the only one. Required, with no default, so that no private key material ships in
	 * the artifact.
	 */
	@NotEmpty(message = JWKS_REQUIRED_MESSAGE)
	private List<@NotBlank(message = JWKS_REQUIRED_MESSAGE) String> jwks = new ArrayList<>();

	/**
	 * How often the JWKS locations are read again to pick up rotated keys. Must be a
	 * fraction of the key rotation interval (28 days by default).
	 */
	@DurationMin(minutes = 1)
	@DurationMax(days = 1)
	private Duration jwksRefreshInterval = Duration.ofHours(1);

	public List<String> getJwks() {
		return this.jwks;
	}

	public void setJwks(List<String> jwks) {
		this.jwks = jwks;
	}

	public Duration getJwksRefreshInterval() {
		return this.jwksRefreshInterval;
	}

	public void setJwksRefreshInterval(Duration jwksRefreshInterval) {
		this.jwksRefreshInterval = jwksRefreshInterval;
	}

}
