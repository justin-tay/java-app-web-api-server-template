package com.example.commons.web.problem;

import java.time.Duration;

/**
 * Thrown when a request needs its user to have authenticated more recently than they did,
 * such as for a sensitive administration action. It is answered with a 401 Problem
 * Details response of type {@link ProblemTypes#REAUTHENTICATION_REQUIRED} carrying the
 * allowed authentication age as {@code max_age}, in seconds, after RFC 9470's step-up
 * authentication challenge. The client re-authenticates by sending the browser to the
 * login URI with {@code max_age=0}.
 */
public class ReauthenticationRequiredException extends RuntimeException {

	private final Duration maxAge;

	/**
	 * Creates the exception.
	 * @param maxAge the greatest time since authentication the request allows
	 */
	public ReauthenticationRequiredException(Duration maxAge) {
		super("Recent authentication is required.");
		this.maxAge = maxAge;
	}

	public Duration getMaxAge() {
		return this.maxAge;
	}

}
