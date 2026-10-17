package com.example.commons.crypto.pkcs11;

import java.security.PrivateKey;
import java.security.Provider;
import java.util.Optional;

import com.example.commons.crypto.PrivateKeyHandle;

/**
 * A key on a token. It cannot be used once it or its token is closed.
 */
public final class Pkcs11PrivateKeyHandle implements PrivateKeyHandle {

	private final Pkcs11Token token;

	private final PrivateKey key;

	private final boolean ownsToken;

	private volatile boolean closed;

	private volatile boolean pkcs1v15KeyTransportAllowed;

	Pkcs11PrivateKeyHandle(Pkcs11Token token, PrivateKey key, boolean ownsToken) {
		this.token = token;
		this.key = key;
		this.ownsToken = ownsToken;
	}

	/**
	 * Allows CMS messages to be decrypted with this key when the content key is wrapped
	 * with RSA PKCS#1 v1.5. Use it only for an RSA key whose provider cannot do OAEP, and
	 * only for messages from senders you trust to be unable to mount a padding oracle
	 * attack.
	 * @return this handle
	 */
	public Pkcs11PrivateKeyHandle allowPkcs1v15KeyTransport() {
		this.pkcs1v15KeyTransportAllowed = true;
		return this;
	}

	@Override
	public boolean pkcs1v15KeyTransportAllowed() {
		return this.pkcs1v15KeyTransportAllowed;
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

	/**
	 * The provider that does ECDH with the key derivation function for this token's
	 * elliptic curve keys, to hand to Bouncy Castle. It is not registered with the JVM.
	 * @return the provider
	 */
	public Provider keyAgreementProvider() {
		return this.token.keyAgreementProvider();
	}

	@Override
	public void close() {
		this.closed = true;
		if (this.ownsToken) {
			this.token.close();
		}
	}

}
