package com.example.commons.crypto.openpgp;

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
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
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
		try {
			Date now = new Date();
			PGPKeyPair master = new JcaPGPKeyPair(
					ec ? PublicKeyAlgorithmTags.ECDSA : PublicKeyAlgorithmTags.RSA_GENERAL, generateKeyPair(ec), now);
			PGPDigestCalculator checksum = new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC)
				.build()
				.get(HashAlgorithmTags.SHA1);
			PGPSignatureSubpacketGenerator masterFlags = new PGPSignatureSubpacketGenerator();
			masterFlags.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
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
				generator.addSubKey(subkey, flags.generate(), null);
				ids[i] = subkey.getKeyID();
			}
			PGPPublicKeyRing publicRing = generator.generatePublicKeyRing();
			PGPSecretKeyRing secretRing = generator.generateSecretKeyRing();
			return new TestKey(armor(publicRing.getEncoded()), armor(secretRing.getEncoded()), master.getKeyID(), ids);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot generate the test key " + name, ex);
		}
	}

	private static String armor(byte[] encoded) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ArmoredOutputStream armored = new ArmoredOutputStream(out)) {
			armored.write(encoded);
		}
		return out.toString("US-ASCII");
	}

}
