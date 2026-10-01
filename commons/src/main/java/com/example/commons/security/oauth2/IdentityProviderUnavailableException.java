package com.example.commons.security.oauth2;

import java.time.Duration;

/**
 * Thrown when a client registration cannot be resolved because the OpenID Provider's
 * discovery metadata could not be fetched yet (see docs/adr/0029). It carries how long to
 * wait before the next attempt, for the {@code Retry-After} header of the 503 response.
 */
public class IdentityProviderUnavailableException extends RuntimeException {

	private final Duration retryAfter;

	public IdentityProviderUnavailableException(String registrationId, Duration retryAfter) {
		super("The OpenID Provider of client registration '" + registrationId + "' is unavailable");
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return this.retryAfter;
	}

}
