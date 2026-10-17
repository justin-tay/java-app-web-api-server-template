package com.example.commons.crypto;

/**
 * A hardware token is not available, for example because it was removed. The library does
 * not reconnect by itself; the caller creates a new key handle.
 */
public class TokenUnavailableException extends CryptoException {

	public TokenUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
