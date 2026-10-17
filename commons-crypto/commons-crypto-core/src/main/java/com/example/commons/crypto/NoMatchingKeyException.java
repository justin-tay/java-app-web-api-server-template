package com.example.commons.crypto;

/**
 * No key the caller supplied matches the message, for example no secret key for any
 * recipient of an encrypted message.
 */
public class NoMatchingKeyException extends CryptoException {

	public NoMatchingKeyException(String message) {
		super(message);
	}

}
