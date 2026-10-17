package com.example.commons.crypto.openpgp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPAlgorithmParameters;
import org.bouncycastle.openpgp.PGPKdfParameters;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

/**
 * Builds OpenPGP public keys around the public half of a key whose private half is
 * somewhere else, such as on a token.
 */
final class TokenKeyRings {

	static final int ENCRYPT = KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private TokenKeyRings() {
	}

	/** The key derivation that RFC 6637 recommends for the size of the curve. */
	private static PGPKdfParameters kdf(ECPublicKey key) {
		int bits = key.getParams().getOrder().bitLength();
		if (bits > 384) {
			return new PGPKdfParameters(HashAlgorithmTags.SHA512, SymmetricKeyAlgorithmTags.AES_256);
		}
		if (bits > 256) {
			return new PGPKdfParameters(HashAlgorithmTags.SHA384, SymmetricKeyAlgorithmTags.AES_192);
		}
		return new PGPKdfParameters(HashAlgorithmTags.SHA256, SymmetricKeyAlgorithmTags.AES_128);
	}

	static KeyPair softwareKeyPair(boolean ec) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance(ec ? "EC" : "RSA", BC);
		if (ec) {
			generator.initialize(new ECGenParameterSpec("secp256r1"));
		}
		else {
			generator.initialize(2048);
		}
		return generator.generateKeyPair();
	}

	/**
	 * A key ring whose only subkey is the given public key with the given key flags. The
	 * primary key is a throwaway software key.
	 */
	static PgpPublicKeys ring(PublicKey subkeyPublic, int flags) throws Exception {
		boolean ec = subkeyPublic instanceof ECPublicKey;
		boolean encryption = (flags & ENCRYPT) != 0;
		int algorithm = ec ? (encryption ? PublicKeyAlgorithmTags.ECDH : PublicKeyAlgorithmTags.ECDSA)
				: PublicKeyAlgorithmTags.RSA_GENERAL;
		PGPAlgorithmParameters parameters = (ec && encryption) ? kdf((ECPublicKey) subkeyPublic) : null;
		Date now = new Date();
		PGPPublicKey pgpPublic = new JcaPGPKeyConverter().setProvider(BC)
			.getPGPPublicKey(algorithm, parameters, subkeyPublic, now);
		// The ring generator wants a private key for the subkey; it is never used.
		PGPKeyPair subkey = new PGPKeyPair(pgpPublic,
				new JcaPGPKeyPair(algorithm, softwareKeyPair(ec), now).getPrivateKey());

		PGPKeyPair master = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, softwareKeyPair(false), now);
		PGPDigestCalculator checksum = new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC)
			.build()
			.get(HashAlgorithmTags.SHA1);
		PGPSignatureSubpacketGenerator masterFlags = new PGPSignatureSubpacketGenerator();
		masterFlags.setKeyFlags(false, KeyFlags.CERTIFY_OTHER);
		PGPKeyRingGenerator generator = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, master,
				"Token key <token@example.com>", checksum, masterFlags.generate(), null,
				new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256)
					.setProvider(BC),
				new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, checksum).setProvider(BC)
					.build("unused".toCharArray()));
		PGPSignatureSubpacketGenerator subkeyFlags = new PGPSignatureSubpacketGenerator();
		subkeyFlags.setKeyFlags(false, flags);
		generator.addSubKey(subkey, subkeyFlags.generate(), null);

		ByteArrayOutputStream armored = new ByteArrayOutputStream();
		try (ArmoredOutputStream out = new ArmoredOutputStream(armored)) {
			out.write(generator.generatePublicKeyRing().getEncoded());
		}
		return PgpPublicKeys.fromArmored(armored.toString(StandardCharsets.US_ASCII));
	}

}
