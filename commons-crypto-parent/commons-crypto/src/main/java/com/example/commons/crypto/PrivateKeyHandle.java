package com.example.commons.crypto;

import java.security.PrivateKey;
import java.security.Provider;
import java.util.Optional;

/**
 * A private key together with the provider that must perform operations with it. For a
 * token key the key material never leaves the token. Close the handle when finished with
 * it; a token handle releases its session and login.
 */
public interface PrivateKeyHandle extends AutoCloseable {

	/**
	 * The key. It may be a non-extractable token object.
	 * @return the private key
	 * @throws IllegalStateException if the handle is closed
	 */
	PrivateKey key();

	/**
	 * The provider that must be used for operations with {@link #key()}. Empty for a
	 * software key, which any provider can use.
	 * @return the provider, if one is required
	 */
	Optional<Provider> provider();

	/**
	 * Releases what the handle holds. Closing more than once is harmless.
	 */
	@Override
	void close();

}
