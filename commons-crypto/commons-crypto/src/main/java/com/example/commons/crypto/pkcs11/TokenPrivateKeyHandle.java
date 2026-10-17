package com.example.commons.crypto.pkcs11;

import java.security.PrivateKey;
import java.security.Provider;
import java.util.Optional;

import com.example.commons.crypto.PrivateKeyHandle;

/**
 * A key on a token. It cannot be used once it or its token is closed.
 */
final class TokenPrivateKeyHandle implements PrivateKeyHandle {

	private final Pkcs11Token token;

	private final PrivateKey key;

	private final boolean ownsToken;

	private volatile boolean closed;

	TokenPrivateKeyHandle(Pkcs11Token token, PrivateKey key, boolean ownsToken) {
		this.token = token;
		this.key = key;
		this.ownsToken = ownsToken;
	}

	@Override
	public PrivateKey key() {
		if (this.closed || this.token.isClosed()) {
			throw new IllegalStateException("The key handle is closed");
		}
		return this.key;
	}

	@Override
	public Optional<Provider> provider() {
		return Optional.of(this.token.provider());
	}

	@Override
	public void close() {
		this.closed = true;
		if (this.ownsToken) {
			this.token.close();
		}
	}

}
