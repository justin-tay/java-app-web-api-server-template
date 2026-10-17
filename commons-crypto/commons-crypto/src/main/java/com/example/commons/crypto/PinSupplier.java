package com.example.commons.crypto;

/**
 * Supplies a token PIN when the library logs in. The library asks once per login and
 * never retries a wrong PIN, because repeated wrong attempts can lock the token. It
 * clears the array it is given as soon as the login is done.
 */
@FunctionalInterface
public interface PinSupplier {

	/**
	 * Returns the PIN for a token.
	 * @param token the token being logged in to
	 * @return the PIN, which the library clears after use; null if there is none
	 */
	char[] pin(TokenInfo token);

}
