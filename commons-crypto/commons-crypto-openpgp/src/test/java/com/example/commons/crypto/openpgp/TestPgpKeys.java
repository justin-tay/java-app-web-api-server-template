package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

/**
 * OpenPGP keys generated for the tests: a certifying and signing primary key with one or
 * more encryption subkeys, protected by the passphrase changeit.
 */
final class TestPgpKeys {

	static final char[] PASSPHRASE = "changeit".toCharArray();

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	static final TestKey RSA = generate("rsa", false, 1);

	static final TestKey EC = generate("ec", true, 1);

	static final TestKey TWO_SUBKEYS = generate("two", false, 2);

	private static final Date TEN_DAYS_AGO = new Date(System.currentTimeMillis() - 10L * 86_400_000L);

	/** The primary key expired nine days ago, so the whole ring is past its time. */
	static final TestKey EXPIRED_PRIMARY = generate("expired-primary", false, 1, TEN_DAYS_AGO, 86_400, 0, false);

	/** The first encryption subkey expired nine days ago; the second is valid. */
	static final TestKey EXPIRED_FIRST_SUBKEY = generate("expired-subkey", false, 2, TEN_DAYS_AGO, 0, 86_400, false);

	/** The primary key is revoked. */
	static final TestKey REVOKED_PRIMARY = generate("revoked-primary", true, 1, new Date(), 0, 0, true);

	private TestPgpKeys() {
	}

	/**
	 * A generated key ring in armored form with the key IDs of its encryption subkeys.
	 */
	record TestKey(String publicArmored, String secretArmored, long masterKeyId, long[] encryptionKeyIds) {

		PgpPublicKeys publicKeys() {
			return PgpPublicKeys.fromArmored(this.publicArmored);
		}

		PgpSecretKeys secretKeys(char[] passphrase) {
			return PgpSecretKeys.fromArmored(this.secretArmored, passphrase);
		}

	}

	private static KeyPair generateKeyPair(boolean ec) throws Exception {
		if (ec) {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BC);
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			return generator.generateKeyPair();
		}
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", BC);
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private static TestKey generate(String name, boolean ec, int encryptionSubkeys) {
		return generate(name, ec, encryptionSubkeys, new Date(), 0, 0, false);
	}

	/**
	 * A key ring made at the given time, optionally with an expiry on the primary key and
	 * on the first encryption subkey (in seconds from the creation time, 0 for none), and
	 * optionally with the primary key revoked.
	 */
	private static TestKey generate(String name, boolean ec, int encryptionSubkeys, Date now, long primaryValidSeconds,
			long firstSubkeyValidSeconds, boolean revokePrimary) {
		try {
			PGPKeyPair master = new JcaPGPKeyPair(
					ec ? PublicKeyAlgorithmTags.ECDSA : PublicKeyAlgorithmTags.RSA_GENERAL, generateKeyPair(ec), now);
			PGPDigestCalculator checksum = new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC)
				.build()
				.get(HashAlgorithmTags.SHA1);
			PGPSignatureSubpacketGenerator masterFlags = new PGPSignatureSubpacketGenerator();
			masterFlags.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
			if (primaryValidSeconds > 0) {
				masterFlags.setKeyExpirationTime(false, primaryValidSeconds);
			}
			// Without preferences GnuPG falls back to SHA-1 when it signs for these keys.
			masterFlags.setPreferredHashAlgorithms(false,
					new int[] { HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA256 });
			masterFlags.setPreferredSymmetricAlgorithms(false, new int[] { SymmetricKeyAlgorithmTags.AES_256,
					SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128 });
			masterFlags.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);
			PGPKeyRingGenerator generator = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, master,
					"Test " + name + " <" + name + "@example.com>", checksum, masterFlags.generate(), null,
					new JcaPGPContentSignerBuilder(master.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
						.setProvider(BC),
					new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, checksum).setProvider(BC)
						.build(PASSPHRASE));
			long[] ids = new long[encryptionSubkeys];
			for (int i = 0; i < encryptionSubkeys; i++) {
				PGPKeyPair subkey = new JcaPGPKeyPair(
						ec ? PublicKeyAlgorithmTags.ECDH : PublicKeyAlgorithmTags.RSA_GENERAL, generateKeyPair(ec),
						now);
				PGPSignatureSubpacketGenerator flags = new PGPSignatureSubpacketGenerator();
				flags.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);
				if (i == 0 && firstSubkeyValidSeconds > 0) {
					flags.setKeyExpirationTime(false, firstSubkeyValidSeconds);
				}
				generator.addSubKey(subkey, flags.generate(), null);
				ids[i] = subkey.getKeyID();
			}
			PGPPublicKeyRing publicRing = generator.generatePublicKeyRing();
			PGPSecretKeyRing secretRing = generator.generateSecretKeyRing();
			if (revokePrimary) {
				publicRing = revoke(publicRing, secretRing);
			}
			return new TestKey(armor(publicRing.getEncoded()), armor(secretRing.getEncoded()), master.getKeyID(), ids);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot generate the test key " + name, ex);
		}
	}

	/**
	 * The public keys of a test key with its primary key revoked afterwards, as if the
	 * owner had published a revocation.
	 */
	static PgpPublicKeys revokedPublicKeys(TestKey key) {
		try {
			PGPPublicKeyRing publicRing = new PGPPublicKeyRing(
					PGPUtil.getDecoderStream(new ByteArrayInputStream(key.publicArmored().getBytes("US-ASCII"))),
					new JcaKeyFingerprintCalculator());
			PGPSecretKeyRing secretRing = new PGPSecretKeyRing(
					PGPUtil.getDecoderStream(new ByteArrayInputStream(key.secretArmored().getBytes("US-ASCII"))),
					new JcaKeyFingerprintCalculator());
			return PgpPublicKeys.fromArmored(armor(revoke(publicRing, secretRing).getEncoded()));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot revoke the test key", ex);
		}
	}

	private static PGPPublicKeyRing revoke(PGPPublicKeyRing publicRing, PGPSecretKeyRing secretRing) throws Exception {
		PGPPublicKey masterPublic = publicRing.getPublicKey();
		PGPPrivateKey masterPrivate = secretRing.getSecretKey()
			.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC).build())
				.setProvider(BC)
				.build(PASSPHRASE));
		PGPSignatureGenerator revoker = new PGPSignatureGenerator(
				new JcaPGPContentSignerBuilder(masterPublic.getAlgorithm(), HashAlgorithmTags.SHA256).setProvider(BC),
				masterPublic);
		revoker.init(PGPSignature.KEY_REVOCATION, masterPrivate);
		PGPPublicKey revoked = PGPPublicKey.addCertification(masterPublic, revoker.generateCertification(masterPublic));
		return PGPPublicKeyRing.insertPublicKey(publicRing, revoked);
	}

	private static String armor(byte[] encoded) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ArmoredOutputStream armored = new ArmoredOutputStream(out)) {
			armored.write(encoded);
		}
		return out.toString("US-ASCII");
	}

}
