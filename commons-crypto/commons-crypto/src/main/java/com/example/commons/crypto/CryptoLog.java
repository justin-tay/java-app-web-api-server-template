package com.example.commons.crypto;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Writes the ECS log event of a cryptographic operation, for the modules of this library:
 * {@code event.category}, {@code event.type}, {@code event.action},
 * {@code event.outcome}, {@code event.reason} for a failure, {@code error.type}, and
 * {@code crypto.format}. A success is logged at {@code DEBUG}, because a library is
 * called often; a failure at {@code WARN}. Nothing from the data, the keys, a PIN or a
 * passphrase is logged, and neither is the message or the stack trace of an exception,
 * which may carry such detail.
 */
public final class CryptoLog {

	private static final Logger LOGGER = LoggerFactory.getLogger(CryptoLog.class);

	private CryptoLog() {
	}

	/**
	 * Runs an operation and logs how it ended. The exception it throws is rethrown.
	 * @param action the operation, such as {@code decrypt}
	 * @param format {@code openpgp}, {@code cms} or {@code pkcs11}
	 * @param operation the operation
	 */
	public static void run(String action, String format, Runnable operation) {
		call(action, format, () -> {
			operation.run();
			return null;
		});
	}

	/**
	 * Runs an operation and logs how it ended. A {@link VerifyResult} with a signature
	 * that is not valid counts as a failure. The exception it throws is rethrown.
	 * @param action the operation, such as {@code verify}
	 * @param format {@code openpgp}, {@code cms} or {@code pkcs11}
	 * @param operation the operation
	 * @param <T> the result type
	 * @return the result of the operation
	 */
	public static <T> T call(String action, String format, Supplier<T> operation) {
		T result;
		try {
			result = operation.get();
		}
		catch (RuntimeException ex) {
			event(true, action, format, reason(ex), ex.getClass().getName());
			throw ex;
		}
		if (result instanceof VerifyResult verified && !verified.allValid()) {
			event(true, action, format, "signature_not_valid", null);
		}
		else {
			event(false, action, format, null, null);
		}
		return result;
	}

	private static void event(boolean failure, String action, String format, String reason, String errorType) {
		LoggingEventBuilder log = (failure ? LOGGER.atWarn() : LOGGER.atDebug());
		log.addKeyValue("event.category", List.of("process"))
			.addKeyValue("event.type", List.of(failure ? "error" : "info"))
			.addKeyValue("event.action", action)
			.addKeyValue("event.outcome", failure ? "failure" : "success")
			.addKeyValue("crypto.format", format);
		if (reason != null) {
			log.addKeyValue("event.reason", reason);
		}
		if (errorType != null) {
			log.addKeyValue("error.type", errorType);
		}
		log.log(failure ? "Cryptographic operation failed: " + action : "Cryptographic operation done: " + action);
	}

	private static String reason(RuntimeException ex) {
		if (ex instanceof IntegrityException) {
			return "integrity_check_failed";
		}
		if (ex instanceof NoMatchingKeyException) {
			return "no_matching_key";
		}
		if (ex instanceof PinRequiredException) {
			return "pin_required";
		}
		if (ex instanceof TokenUnavailableException) {
			return "token_unavailable";
		}
		return "failed";
	}

}
