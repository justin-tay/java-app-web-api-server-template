package com.example.commons.crypto.pkcs11;

import java.io.IOException;
import java.security.AuthProvider;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.PinSupplier;
import com.example.commons.crypto.TokenFailures;
import com.example.commons.crypto.TokenInfo;
import com.example.commons.crypto.TokenUnavailableException;

/**
 * A logged-in PKCS#11 token. It owns the login: closing it logs out, after which the keys
 * it gave out cannot be used. It is safe to use from several threads. The library does
 * not reconnect when a token is removed; operations then fail and the caller opens a new
 * token.
 */
public final class Pkcs11Token implements AutoCloseable {

	private static final AtomicInteger COUNTER = new AtomicInteger();

	private final Provider provider;

	private final KeyStore keyStore;

	private final Provider keyAgreementProvider;

	private volatile boolean closed;

	private Pkcs11Token(Provider provider, KeyStore keyStore) {
		this.provider = provider;
		this.keyStore = keyStore;
		this.keyAgreementProvider = new TokenEcdhProvider(provider);
	}

	/**
	 * Logs in to a token. The PIN is asked for once and never retried.
	 * @param config where the token is
	 * @param pins supplies the PIN
	 * @return the logged-in token
	 * @throws TokenUnavailableException if the module cannot be loaded or the token is
	 * absent
	 * @throws PinRequiredException if no PIN is supplied, or it is wrong or the token is
	 * locked
	 */
	public static Pkcs11Token open(Pkcs11Config config, PinSupplier pins) {
		String name = "commons-crypto-" + COUNTER.incrementAndGet();
		Provider provider = provider(config, name);
		char[] pin = pins.pin(new TokenInfo(name, config.library()));
		if (pin == null) {
			throw new PinRequiredException("A token PIN is required");
		}
		try {
			KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
			keyStore.load(null, pin);
			return new Pkcs11Token(provider, keyStore);
		}
		catch (IOException | GeneralSecurityException | RuntimeException ex) {
			throw loginFailure(ex);
		}
		finally {
			Arrays.fill(pin, '\0');
		}
	}

	/**
	 * Logs in to a token and returns one of its keys, with a handle that logs out when it
	 * is closed.
	 * @param config where the token is
	 * @param pins supplies the PIN
	 * @param alias the alias of the key
	 * @return a handle that owns the token
	 */
	public static Pkcs11PrivateKeyHandle openPrivateKey(Pkcs11Config config, PinSupplier pins, String alias) {
		Pkcs11Token token = open(config, pins);
		try {
			return new Pkcs11PrivateKeyHandle(token, token.key(alias), true);
		}
		catch (RuntimeException ex) {
			token.close();
			throw ex;
		}
	}

	private static Provider provider(Pkcs11Config config, String name) {
		Provider base = Security.getProvider("SunPKCS11");
		if (base == null) {
			throw new TokenUnavailableException("The SunPKCS11 provider is not available in this JDK", null);
		}
		try {
			return base.configure(config.providerConfiguration(name));
		}
		catch (RuntimeException ex) {
			throw new TokenUnavailableException("Cannot load the PKCS#11 module " + config.library().getFileName(), ex);
		}
	}

	/**
	 * The key with the given alias.
	 * @param alias the alias of a private key that has a certificate on the token
	 * @return a handle for the key; closing it leaves the token logged in
	 * @throws CryptoException if there is no private key with that alias
	 * @throws IllegalStateException if the token is closed
	 */
	public Pkcs11PrivateKeyHandle privateKey(String alias) {
		return new Pkcs11PrivateKeyHandle(this, key(alias), false);
	}

	private PrivateKey key(String alias) {
		ensureOpen();
		Key key;
		try {
			synchronized (this.keyStore) {
				key = this.keyStore.getKey(alias, null);
			}
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			throw failure("Cannot read the key with alias " + alias, ex);
		}
		if (!(key instanceof PrivateKey privateKey)) {
			throw new CryptoException("No private key with alias " + alias
					+ " on the token; a key is only listed if the token also holds a certificate for it");
		}
		return privateKey;
	}

	/**
	 * The certificate with the given alias.
	 * @param alias the alias of a certificate on the token
	 * @return the certificate
	 * @throws CryptoException if there is no X.509 certificate with that alias
	 * @throws IllegalStateException if the token is closed
	 */
	public X509Certificate certificate(String alias) {
		ensureOpen();
		try {
			Certificate certificate;
			synchronized (this.keyStore) {
				certificate = this.keyStore.getCertificate(alias);
			}
			if (certificate instanceof X509Certificate x509) {
				return x509;
			}
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			throw failure("Cannot read the certificate with alias " + alias, ex);
		}
		throw new CryptoException("No certificate with alias " + alias + " on the token");
	}

	/**
	 * The aliases of the keys and certificates on the token.
	 * @return the aliases
	 * @throws IllegalStateException if the token is closed
	 */
	public List<String> aliases() {
		ensureOpen();
		try {
			synchronized (this.keyStore) {
				return Collections.list(this.keyStore.aliases());
			}
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			throw failure("Cannot list the keys on the token", ex);
		}
	}

	Provider provider() {
		return this.provider;
	}

	Provider keyAgreementProvider() {
		return this.keyAgreementProvider;
	}

	boolean isClosed() {
		return this.closed;
	}

	private void ensureOpen() {
		if (this.closed) {
			throw new IllegalStateException("The token is closed");
		}
	}

	/**
	 * Logs out. Closing more than once is harmless.
	 */
	@Override
	public synchronized void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		if (this.provider instanceof AuthProvider authProvider) {
			try {
				authProvider.logout();
			}
			catch (LoginException | RuntimeException ex) {
				// The token may already be gone; there is nothing left to release.
			}
		}
	}

	private static CryptoException loginFailure(Exception ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof FailedLoginException) {
				return new PinRequiredException("The token PIN is wrong or the token is locked", ex);
			}
		}
		return failure("Cannot open the token", ex);
	}

	/** Maps a token failure to the unavailable exception when the token is gone. */
	static CryptoException failure(String message, Exception ex) {
		return TokenFailures.translate(ex, message);
	}

}
