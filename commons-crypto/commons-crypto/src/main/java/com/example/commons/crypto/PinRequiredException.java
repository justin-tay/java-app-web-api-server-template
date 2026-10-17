package com.example.commons.crypto;

/**
 * A token PIN is wrong or the token is locked. The library never retries a PIN by itself,
 * because repeated wrong attempts can lock the token.
 */
public class PinRequiredException extends CryptoException {

	public PinRequiredException(String message) {
		super(message);
	}

	public PinRequiredException(String message, Throwable cause) {
		super(message, cause);
	}

}
