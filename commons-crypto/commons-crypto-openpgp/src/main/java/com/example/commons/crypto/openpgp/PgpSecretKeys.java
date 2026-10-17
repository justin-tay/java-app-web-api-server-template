package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.PrivateKeyHandle;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPPrivateKey;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

/**
 * OpenPGP secret keys: either held in software, read from armored or binary key rings and
 * unlocked with a passphrase only when a message needs them, or a key on a PKCS#11 token
 * that is paired with its public key. The passphrase of software keys is copied and kept
 * until {@link #clear()} is called.
 */
public final class PgpSecretKeys {

	private final PGPSecretKeyRingCollection rings;

	private final char[] passphrase;

	private final PrivateKeyHandle tokenKey;

	private final PGPPublicKey tokenPublicKey;

	private PgpSecretKeys(PGPSecretKeyRingCollection rings, char[] passphrase) {
		this.rings = rings;
		this.passphrase = passphrase.clone();
		this.tokenKey = null;
		this.tokenPublicKey = null;
	}

	private PgpSecretKeys(PrivateKeyHandle tokenKey, PGPPublicKey tokenPublicKey) {
		this.rings = null;
		this.passphrase = new char[0];
		this.tokenKey = tokenKey;
		this.tokenPublicKey = tokenPublicKey;
	}

	/**
	 * Reads secret keys from ASCII armor.
	 * @param armored one or more armored secret key blocks
	 * @param passphrase the passphrase that protects the keys
	 * @return the keys
	 * @throws CryptoException if no key can be read
	 */
	public static PgpSecretKeys fromArmored(String armored, char[] passphrase) {
		return read(new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)), passphrase);
	}

	/**
	 * Reads secret keys from armored or binary key rings. The stream is not closed.
	 * @param in the key rings
	 * @param passphrase the passphrase that protects the keys
	 * @return the keys
	 * @throws CryptoException if no key can be read
	 */
	public static PgpSecretKeys read(InputStream in, char[] passphrase) {
		try {
			PGPSecretKeyRingCollection rings = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(in),
					new JcaKeyFingerprintCalculator());
			if (rings.size() == 0) {
				throw new CryptoException("No OpenPGP secret key found");
			}
			return new PgpSecretKeys(rings, passphrase);
		}
		catch (IOException | PGPException | RuntimeException ex) {
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new CryptoException("Cannot read the OpenPGP secret keys", ex);
		}
	}

	/**
	 * Pairs a key on a token with its OpenPGP public key. A token holds no OpenPGP data,
	 * so the caller supplies the public keys; the one that matches the token key is found
	 * by having the token sign a test value and verifying it with each candidate, which
	 * also means the token key must be allowed to sign. Which of the keys that is decides
	 * what it can be used for: its key flags, not the token, say whether it signs or
	 * decrypts.
	 * @param key the key on the token
	 * @param publicKeys the OpenPGP public keys, which include the one for the token key
	 * @return the keys
	 * @throws CryptoException if none of the public keys matches the token key
	 */
	public static PgpSecretKeys token(PrivateKeyHandle key, PgpPublicKeys publicKeys) {
		for (PGPPublicKey candidate : publicKeys.allKeys()) {
			if (matches(key, candidate)) {
				return new PgpSecretKeys(key, candidate);
			}
		}
		throw new CryptoException("None of the OpenPGP public keys matches the key on the token; "
				+ "the token key must also be allowed to sign, so that the match can be checked");
	}

	private static boolean matches(PrivateKeyHandle key, PGPPublicKey candidate) {
		try {
			PublicKey converted = new JcaPGPKeyConverter().setProvider(PgpProviders.BC).getPublicKey(candidate);
			boolean ec = converted instanceof ECPublicKey;
			// Bouncy Castle's key can carry the name ECDH, which the JDK's signature does
			// not accept.
			PublicKey publicKey = ec
					? KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(converted.getEncoded()))
					: converted;
			String algorithm = ec ? "SHA256withECDSA" : "SHA256withRSA";
			byte[] challenge = new byte[32];
			new SecureRandom().nextBytes(challenge);
			Signature signer = Signature.getInstance(algorithm, key.provider().orElse(PgpProviders.BC));
			signer.initSign(key.key());
			signer.update(challenge);
			byte[] signature = signer.sign();
			Signature verifier = Signature.getInstance(algorithm);
			verifier.initVerify(publicKey);
			verifier.update(challenge);
			return verifier.verify(signature);
		}
		catch (GeneralSecurityException | PGPException | RuntimeException ex) {
			return false;
		}
	}

	/**
	 * Overwrites the stored passphrase. Software keys cannot be unlocked afterwards.
	 */
	public void clear() {
		Arrays.fill(this.passphrase, '\0');
	}

	/**
	 * The token key, if these keys are on a token.
	 */
	Optional<PrivateKeyHandle> token() {
		return Optional.ofNullable(this.tokenKey);
	}

	/**
	 * Picks the first key that may sign (a primary key or subkey, by its key flags) and
	 * unlocks it.
	 * @throws CryptoException if no key may sign
	 * @throws PinRequiredException if the passphrase is wrong
	 */
	SigningKey signingKey() {
		if (this.tokenKey != null) {
			if (!PgpPublicKeys.canSign(this.tokenPublicKey)) {
				throw new CryptoException("The OpenPGP key on the token is not allowed to sign");
			}
			return new SigningKey(this.tokenPublicKey, new JcaPGPPrivateKey(this.tokenPublicKey, this.tokenKey.key()),
					this.tokenKey.provider());
		}
		for (Iterator<PGPSecretKeyRing> rings = this.rings.getKeyRings(); rings.hasNext();) {
			for (Iterator<PGPSecretKey> keys = rings.next().getSecretKeys(); keys.hasNext();) {
				PGPSecretKey key = keys.next();
				if (key.isSigningKey() && PgpPublicKeys.canSign(key.getPublicKey())) {
					return new SigningKey(key.getPublicKey(), privateKey(key.getKeyID()), Optional.empty());
				}
			}
		}
		throw new CryptoException("The OpenPGP keys have no usable signing key");
	}

	/**
	 * A signing key with its public half, for the key ID and algorithm, and the provider
	 * that must perform the signing if the key is on a token.
	 */
	record SigningKey(PGPPublicKey publicKey, PGPPrivateKey privateKey, Optional<java.security.Provider> provider) {
	}

	/**
	 * Unlocks the key with the given ID, or returns null if there is none.
	 * @throws PinRequiredException if the passphrase is wrong
	 */
	PGPPrivateKey privateKey(long keyId) {
		if (this.tokenKey != null) {
			return (keyId == this.tokenPublicKey.getKeyID())
					? new JcaPGPPrivateKey(this.tokenPublicKey, this.tokenKey.key()) : null;
		}
		try {
			PGPSecretKey key = this.rings.getSecretKey(keyId);
			if (key == null) {
				return null;
			}
			return key.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(PgpProviders.BC).build())
				.setProvider(PgpProviders.BC)
				.build(this.passphrase));
		}
		catch (PGPException ex) {
			throw new PinRequiredException("The passphrase of the OpenPGP key is wrong", ex);
		}
	}

}
