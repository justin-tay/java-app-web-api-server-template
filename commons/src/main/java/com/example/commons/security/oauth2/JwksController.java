package com.example.commons.security.oauth2;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

/**
 * Publishes the public keys of the application's JWKS, so the identity provider can
 * verify the {@code private_key_jwt} client assertions the application signs.
 */
@RestController
public class JwksController {

	/**
	 * Path of the public JWKS.
	 */
	public static final String JWKS_PATH = "/oauth2/jwks";

	private final JWKSet jwkset;

	public JwksController(JWKSet jwkset) {
		this.jwkset = jwkset;
	}

	/**
	 * Returns the JWKS public keys. {@link JWKSet#toString()} serializes public key
	 * parameters only, never the private ones.
	 * @return the public JWKS
	 */
	@GetMapping(path = JWKS_PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
	public String jwks() {
		return this.jwkset.toString();
	}

}
