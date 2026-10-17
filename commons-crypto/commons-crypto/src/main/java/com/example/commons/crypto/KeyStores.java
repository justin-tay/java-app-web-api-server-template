package com.example.commons.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;

import com.example.commons.crypto.pkcs11.Pkcs11Config;
import com.example.commons.crypto.pkcs11.Pkcs11PrivateKeyHandle;
import com.example.commons.crypto.pkcs11.Pkcs11Token;

/**
 * Loads private keys into {@link PrivateKeyHandle}s. The passwords are used for the call
 * and not kept; clearing the array afterwards is up to the caller.
 */
public final class KeyStores {

	private KeyStores() {
	}

	/**
	 * Loads a private key from a PKCS#12 file.
	 * @param file the PKCS#12 file
	 * @param password the store password, which is also used as the key password
	 * @param alias the alias of the key entry
	 * @return a handle for the key
	 * @throws CryptoException if the file cannot be read, the password is wrong or the
	 * alias is not a private key
	 */
	public static PrivateKeyHandle pkcs12(Path file, char[] password, String alias) {
		KeyStore keyStore;
		try (InputStream in = Files.newInputStream(file)) {
			keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(in, password);
		}
		catch (IOException | GeneralSecurityException ex) {
			throw new CryptoException("Cannot load the PKCS#12 key store " + file.getFileName(), ex);
		}
		return keyStore(keyStore, alias, password);
	}

	/**
	 * Logs in to a PKCS#11 token and returns one of its keys. The key stays on the token;
	 * closing the handle logs out.
	 * @param config where the token is
	 * @param pins supplies the PIN, asked for once and never retried
	 * @param alias the alias of the key
	 * @return a handle for the key that owns the login
	 * @throws TokenUnavailableException if the module cannot be loaded or the token is
	 * absent
	 * @throws PinRequiredException if the PIN is missing or wrong, or the token is locked
	 * @throws CryptoException if there is no private key with that alias
	 */
	public static Pkcs11PrivateKeyHandle pkcs11(Pkcs11Config config, PinSupplier pins, String alias) {
		return Pkcs11Token.openPrivateKey(config, pins, alias);
	}

	/**
	 * Takes a private key from a key store that is already loaded.
	 * @param keyStore the loaded key store
	 * @param alias the alias of the key entry
	 * @param keyPassword the password of the key entry
	 * @return a handle for the key
	 * @throws CryptoException if the alias is not a private key or the password is wrong
	 */
	public static PrivateKeyHandle keyStore(KeyStore keyStore, String alias, char[] keyPassword) {
		Key key;
		try {
			key = keyStore.getKey(alias, keyPassword);
		}
		catch (GeneralSecurityException ex) {
			throw new CryptoException("Cannot recover the key with alias " + alias, ex);
		}
		if (!(key instanceof PrivateKey privateKey)) {
			throw new CryptoException("No private key with alias " + alias);
		}
		return new SoftwarePrivateKeyHandle(privateKey);
	}

}
