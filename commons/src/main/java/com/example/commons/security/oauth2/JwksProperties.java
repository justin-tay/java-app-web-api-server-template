package com.example.commons.security.oauth2;

import jakarta.validation.constraints.NotBlank;

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
	static final String JWKS_REQUIRED_MESSAGE = "must be set to the location of this deployment's private JWKS "
			+ "(for example file:/run/secrets/jwks.json) because a client registration uses private_key_jwt; "
			+ "no JWKS is packaged with the application, see docs/adr/0018";

	/**
	 * Resource location of the private JWKS used for {@code private_key_jwt} client
	 * authentication. Required, with no default, so that no private key material ships in
	 * the artifact.
	 */
	@NotBlank(message = JWKS_REQUIRED_MESSAGE)
	private String jwks;

	public String getJwks() {
		return this.jwks;
	}

	public void setJwks(String jwks) {
		this.jwks = jwks;
	}

}
