package com.example.commons.security.oauth2;

/**
 * An OpenID Provider discovery failure. A definitive one (a 4xx response, a malformed
 * document, an issuer mismatch) is a misconfiguration that retrying cannot fix; a
 * transient one (no connection, a timeout, a 5xx response) is the provider being down.
 */
public class DiscoveryException extends RuntimeException {

	private final boolean definitive;

	public DiscoveryException(boolean definitive, String reason, Throwable cause) {
		super(reason, cause);
		this.definitive = definitive;
	}

	public boolean isDefinitive() {
		return this.definitive;
	}

}
