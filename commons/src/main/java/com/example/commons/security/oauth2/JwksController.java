package com.example.commons.security.oauth2;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the public keys of the application's JWKS, so the identity provider can
 * verify the {@code private_key_jwt} client assertions the application signs and encrypt
 * ID tokens to it. Which keys are published follows the rotation rules of
 * {@link RefreshingJwks#publicJwks()}.
 */
@RestController
public class JwksController {

	/**
	 * Path of the public JWKS.
	 */
	public static final String JWKS_PATH = "/oauth2/jwks";

	private final RefreshingJwks jwks;

	public JwksController(RefreshingJwks jwks) {
		this.jwks = jwks;
	}

	/**
	 * Returns the current public keys. {@link RefreshingJwks#publicJwks()} holds public
	 * key parameters only, and {@link com.nimbusds.jose.jwk.JWKSet#toString()} serializes
	 * public key parameters only, never the private ones.
	 * @return the public JWKS
	 */
	@GetMapping(path = JWKS_PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
	public String jwks() {
		return this.jwks.publicJwks().toString();
	}

}
