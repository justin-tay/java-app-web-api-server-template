package com.example.commons.crypto;

/**
 * A message failed an integrity or signature check. Output written before this was thrown
 * must be discarded, because the check is made after the data has been streamed.
 */
public class IntegrityException extends CryptoException {

	public IntegrityException(String message) {
		super(message);
	}

	public IntegrityException(String message, Throwable cause) {
		super(message, cause);
	}

}
