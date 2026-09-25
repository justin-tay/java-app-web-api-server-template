package com.example.commons.security.oauth2;

import com.nimbusds.jose.proc.JWEKeySelector;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Decryption of ID tokens that the identity provider encrypts to the application, as a
 * JWE (RFC 7516) nesting its signed JWT. When a bean of this type exists, the commons ID
 * token decoder decrypts with its {@link #keySelector()}, and rejects an ID token that is
 * not encrypted while {@link #isRequired()}. See docs/adr/0020.
 */
public interface IdTokenDecryption {

	/**
	 * Returns the selector of the private keys a JWE may be decrypted with.
	 * @return the key selector
	 */
	JWEKeySelector<SecurityContext> keySelector();

	/**
	 * Returns whether ID tokens must be encrypted, so a plain signed ID token is
	 * rejected.
	 * @return whether ID tokens must be encrypted
	 */
	boolean isRequired();

}
