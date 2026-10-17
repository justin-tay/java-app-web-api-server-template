package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureStatus;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

/**
 * OpenPGP public keys, read from armored or binary key rings. The library does not decide
 * whether a key is trusted; it only checks that a subkey is allowed to encrypt, and is
 * not revoked or expired, before encrypting to it.
 */
public final class PgpPublicKeys {

	private final PGPPublicKeyRingCollection rings;

	private PgpPublicKeys(PGPPublicKeyRingCollection rings) {
		this.rings = rings;
	}

	/**
	 * Reads public keys from ASCII armor.
	 * @param armored one or more armored public key blocks
	 * @return the keys
	 * @throws CryptoException if no key can be read
	 */
	public static PgpPublicKeys fromArmored(String armored) {
		return read(new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)));
	}

	/**
	 * Reads public keys from armored or binary key rings. The stream is not closed.
	 * @param in the key rings
	 * @return the keys
	 * @throws CryptoException if no key can be read
	 */
	public static PgpPublicKeys read(InputStream in) {
		try {
			PGPPublicKeyRingCollection rings = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in),
					new JcaKeyFingerprintCalculator());
			if (rings.size() == 0) {
				throw new CryptoException("No OpenPGP public key found");
			}
			return new PgpPublicKeys(rings);
		}
		catch (IOException | PGPException | RuntimeException ex) {
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new CryptoException("Cannot read the OpenPGP public keys", ex);
		}
	}

	/**
	 * Every key that may encrypt: not a primary key that is only for certifying and
	 * signing, and neither revoked nor expired.
	 */
	List<PGPPublicKey> encryptionKeys() {
		List<PGPPublicKey> keys = new ArrayList<>();
		for (Iterator<PGPPublicKeyRing> rings = this.rings.getKeyRings(); rings.hasNext();) {
			PGPPublicKeyRing ring = rings.next();
			PGPPublicKey primary = ring.getPublicKey();
			if (primary.hasRevocation() || expired(primary)) {
				// The subkeys hang on the primary key, so they go with it.
				continue;
			}
			for (Iterator<PGPPublicKey> candidates = ring.getPublicKeys(); candidates.hasNext();) {
				PGPPublicKey key = candidates.next();
				if (canEncrypt(key) && !key.hasRevocation() && !expired(key)) {
					keys.add(key);
				}
			}
		}
		return keys;
	}

	/** Every key in every ring: primary keys and subkeys. */
	List<PGPPublicKey> allKeys() {
		List<PGPPublicKey> keys = new ArrayList<>();
		for (Iterator<PGPPublicKeyRing> rings = this.rings.getKeyRings(); rings.hasNext();) {
			rings.next().getPublicKeys().forEachRemaining(keys::add);
		}
		return keys;
	}

	/**
	 * The key with the given ID in any of the rings, or null if there is none.
	 */
	PGPPublicKey publicKey(long keyId) {
		return this.rings.getPublicKey(keyId);
	}

	/**
	 * Whether the key may sign data: its key flags allow it, or it has none and so is
	 * assumed to; and it is neither revoked nor expired.
	 */
	static boolean canSign(PGPPublicKey key) {
		int flags = keyFlags(key);
		boolean allowed = (flags == 0) ? key.isMasterKey() : (flags & KeyFlags.SIGN_DATA) != 0;
		return allowed && !key.hasRevocation() && !expired(key);
	}

	private static boolean canEncrypt(PGPPublicKey key) {
		if (!key.isEncryptionKey()) {
			return false;
		}
		int flags = keyFlags(key);
		if (flags == 0) {
			// Keys without key flags: only a subkey is assumed to be for encryption.
			return !key.isMasterKey();
		}
		return (flags & (KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE)) != 0;
	}

	private static int keyFlags(PGPPublicKey key) {
		int flags = 0;
		for (Iterator<PGPSignature> signatures = key.getSignatures(); signatures.hasNext();) {
			PGPSignature signature = signatures.next();
			if (signature.getHashedSubPackets() != null && signature.getHashedSubPackets().getKeyFlags() != 0) {
				flags = signature.getHashedSubPackets().getKeyFlags();
			}
		}
		return flags;
	}

	private static boolean expired(PGPPublicKey key) {
		return expiredAt(key, Instant.now());
	}

	private static boolean expiredAt(PGPPublicKey key, Instant time) {
		long validSeconds = key.getValidSeconds();
		return validSeconds > 0 && key.getCreationTime().toInstant().plusSeconds(validSeconds).isBefore(time);
	}

	/**
	 * Why the key should not be trusted for a signature made at the given time: revoked
	 * (the key or its primary key), or expired at that time.
	 * @return {@code KEY_REVOKED}, {@code KEY_EXPIRED}, or null if there is no reason
	 */
	SignatureStatus keyProblem(long keyId, Instant time) {
		PGPPublicKeyRing ring = this.rings.getPublicKeyRing(keyId);
		if (ring == null) {
			return null;
		}
		PGPPublicKey key = ring.getPublicKey(keyId);
		PGPPublicKey primary = ring.getPublicKey();
		if (key.hasRevocation() || primary.hasRevocation()) {
			return SignatureStatus.KEY_REVOKED;
		}
		Instant at = (time != null) ? time : Instant.now();
		return (expiredAt(key, at) || expiredAt(primary, at)) ? SignatureStatus.KEY_EXPIRED : null;
	}

}
