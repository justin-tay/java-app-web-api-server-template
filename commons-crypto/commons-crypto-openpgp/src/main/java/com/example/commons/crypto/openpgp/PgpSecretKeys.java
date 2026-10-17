package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PinRequiredException;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

/**
 * OpenPGP secret keys held in software, read from armored or binary key rings and
 * unlocked with a passphrase only when a message needs them. The passphrase is copied and
 * kept until {@link #clear()} is called.
 */
public final class PgpSecretKeys {

	private final PGPSecretKeyRingCollection rings;

	private final char[] passphrase;

	private PgpSecretKeys(PGPSecretKeyRingCollection rings, char[] passphrase) {
		this.rings = rings;
		this.passphrase = passphrase.clone();
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
	 * Overwrites the stored passphrase. The keys cannot be unlocked afterwards.
	 */
	public void clear() {
		java.util.Arrays.fill(this.passphrase, '\0');
	}

	/**
	 * Picks the first key that may sign (a primary key or subkey, by its key flags) and
	 * unlocks it.
	 * @throws CryptoException if no key may sign
	 * @throws PinRequiredException if the passphrase is wrong
	 */
	SigningKey signingKey() {
		for (Iterator<PGPSecretKeyRing> rings = this.rings.getKeyRings(); rings.hasNext();) {
			for (Iterator<PGPSecretKey> keys = rings.next().getSecretKeys(); keys.hasNext();) {
				PGPSecretKey key = keys.next();
				if (key.isSigningKey() && PgpPublicKeys.canSign(key.getPublicKey())) {
					return new SigningKey(key.getPublicKey(), privateKey(key.getKeyID()));
				}
			}
		}
		throw new CryptoException("The OpenPGP keys have no usable signing key");
	}

	/** A signing key with its public half, for the key ID and algorithm. */
	record SigningKey(PGPPublicKey publicKey, PGPPrivateKey privateKey) {
	}

	/**
	 * Unlocks the key with the given ID, or returns null if there is none.
	 * @throws PinRequiredException if the passphrase is wrong
	 */
	PGPPrivateKey privateKey(long keyId) {
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
