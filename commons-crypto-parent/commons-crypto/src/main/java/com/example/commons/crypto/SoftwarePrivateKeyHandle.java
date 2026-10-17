package com.example.commons.crypto;

import java.security.PrivateKey;
import java.security.Provider;
import java.util.Optional;

/**
 * A handle for a key held in memory, which any provider can use.
 */
final class SoftwarePrivateKeyHandle implements PrivateKeyHandle {

	private volatile PrivateKey key;

	SoftwarePrivateKeyHandle(PrivateKey key) {
		this.key = key;
	}

	@Override
	public PrivateKey key() {
		PrivateKey current = this.key;
		if (current == null) {
			throw new IllegalStateException("The key handle is closed");
		}
		return current;
	}

	@Override
	public Optional<Provider> provider() {
		return Optional.empty();
	}

	@Override
	public void close() {
		this.key = null;
	}

}
