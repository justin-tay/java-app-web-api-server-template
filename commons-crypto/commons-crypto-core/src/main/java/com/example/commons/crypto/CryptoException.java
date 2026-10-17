package com.example.commons.crypto;

/**
 * The base of every exception the commons-crypto modules throw. It is unchecked. A
 * message never contains a PIN, key material or message content.
 */
public class CryptoException extends RuntimeException {

	public CryptoException(String message) {
		super(message);
	}

	public CryptoException(String message, Throwable cause) {
		super(message, cause);
	}

}
