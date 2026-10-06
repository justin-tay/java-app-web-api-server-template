package com.example.commons.web.problem;

/**
 * Signals a request-level validation failure, outside Bean Validation, whose message is
 * safe to return to the client.
 * <p>
 * Use this instead of {@link IllegalArgumentException} for intentional, client-facing
 * validation failures. {@code IllegalArgumentException} is also the JDK's and third-party
 * libraries' general-purpose "invalid argument" exception, thrown in places that were
 * never written with an external audience in mind;
 * {@link ApiResponseEntityExceptionHandler} therefore never discloses its message, while
 * a {@link BadRequestException} message is always included in the response. When the
 * failure belongs to one field, name it, and the response points at it as a Bean
 * Validation failure does.
 */
public class BadRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String field;

	public BadRequestException(String message) {
		this(null, message);
	}

	/**
	 * Creates an exception for one field of the request body.
	 * @param field the field, as a dotted path such as {@code review.interval}, or null
	 * @param message the message, safe to return to the client
	 */
	public BadRequestException(String field, String message) {
		super(message);
		this.field = field;
	}

	/**
	 * Returns the field the failure belongs to.
	 * @return the dotted path of the field, or null when it belongs to the request as a
	 * whole
	 */
	public String getField() {
		return this.field;
	}

}
