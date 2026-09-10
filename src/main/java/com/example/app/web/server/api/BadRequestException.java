package com.example.app.web.server.api;

/**
 * Signals a request-level validation failure, outside Bean Validation, whose message is
 * safe to return to the client.
 * <p>
 * Use this instead of {@link IllegalArgumentException} for intentional, client-facing
 * validation failures. {@code IllegalArgumentException} is also the JDK's and third-party
 * libraries' general-purpose "invalid argument" exception, thrown in places that were
 * never written with an external audience in mind;
 * {@link ApiResponseEntityExceptionHandler} therefore never discloses its message, while
 * a {@link BadRequestException} message is always included in the response.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
