package com.example.commons.crypto;

import java.util.List;
import java.util.Optional;

/**
 * Recognises the failures that mean a PKCS#11 token has gone away, wherever in the cause
 * chain of an exception from a provider or from Bouncy Castle they show up. For the
 * format modules and the token support; not meant for applications.
 */
public final class TokenFailures {

	private static final List<String> UNAVAILABLE = List.of("CKR_TOKEN_NOT_PRESENT", "CKR_DEVICE_REMOVED",
			"CKR_DEVICE_ERROR", "CKR_SLOT_ID_INVALID", "CKR_TOKEN_NOT_RECOGNIZED", "CKR_SESSION_HANDLE_INVALID",
			"CKR_SESSION_CLOSED");

	private TokenFailures() {
	}

	/**
	 * Finds out whether a failure is a token that is no longer available.
	 * @param failure the exception to look through, with its causes
	 * @return the exception to throw instead, if it is
	 */
	public static Optional<TokenUnavailableException> unavailable(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			String text = String.valueOf(cause.getMessage());
			for (String code : UNAVAILABLE) {
				if (text.contains(code)) {
					return Optional.of(new TokenUnavailableException("The token is not available", failure));
				}
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return Optional.empty();
	}

	/**
	 * What to throw for an unchecked failure: a {@code TokenUnavailableException} if the
	 * token has gone away, else the failure itself.
	 * @param failure the failure
	 * @return the exception to throw
	 */
	public static RuntimeException translate(RuntimeException failure) {
		return unavailable(failure).map(RuntimeException.class::cast).orElse(failure);
	}

	/**
	 * What to throw for a checked failure of an operation: a
	 * {@code TokenUnavailableException} if the token has gone away, else a
	 * {@code CryptoException} with the message.
	 * @param failure the failure
	 * @param message the message for the {@code CryptoException}
	 * @return the exception to throw
	 */
	public static CryptoException translate(Exception failure, String message) {
		return unavailable(failure).map(CryptoException.class::cast)
			.orElseGet(() -> new CryptoException(message, failure));
	}

}
