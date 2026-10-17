package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The opt-in check that the signing key was neither revoked nor expired when it signed.
 */
class PgpKeyValidityTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] TEXT = "Some text to sign".getBytes(StandardCharsets.UTF_8);

	private static byte[] detachedSignature(TestPgpKeys.TestKey key, Date createdAt) throws Exception {
		PGPSecretKeyRing ring = new JcaPGPSecretKeyRingCollection(PGPUtil
			.getDecoderStream(new ByteArrayInputStream(key.secretArmored().getBytes(StandardCharsets.US_ASCII))))
			.getKeyRings()
			.next();
		PGPPrivateKey privateKey = ring.getSecretKey()
			.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC).build())
				.setProvider(BC)
				.build(TestPgpKeys.PASSPHRASE));
		PGPSignatureGenerator generator = new PGPSignatureGenerator(
				new JcaPGPContentSignerBuilder(ring.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
					.setProvider(BC),
				ring.getPublicKey());
		generator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
		PGPSignatureSubpacketGenerator subpackets = new PGPSignatureSubpacketGenerator();
		subpackets.setSignatureCreationTime(false, createdAt);
		generator.setHashedSubpackets(subpackets.generate());
		generator.update(TEXT);
		ByteArrayOutputStream signature = new ByteArrayOutputStream();
		BCPGOutputStream out = new BCPGOutputStream(signature);
		generator.generate().encode(out);
		out.close();
		return signature.toByteArray();
	}

	private static SignatureStatus verify(byte[] signature, PgpPublicKeys keys, boolean check) {
		VerifyResult result = PgpVerifier.create()
			.verifyDetached(VerifyRequest.builder().keys(keys).checkKeyValidity(check).build(),
					new ByteArrayInputStream(TEXT), new ByteArrayInputStream(signature));
		return result.signatures().get(0).status();
	}

	@Test
	void aValidKeyIsStillValidWhenTheCheckIsOn() throws Exception {
		byte[] signature = detachedSignature(TestPgpKeys.RSA, new Date());

		assertThat(verify(signature, TestPgpKeys.RSA.publicKeys(), true)).isEqualTo(SignatureStatus.VALID);
	}

	@Test
	void aRevokedKeyIsReportedOnlyWhenTheCheckIsOn() throws Exception {
		byte[] signature = detachedSignature(TestPgpKeys.RSA, new Date());
		PgpPublicKeys revoked = TestPgpKeys.revokedPublicKeys(TestPgpKeys.RSA);

		assertThat(verify(signature, revoked, false)).isEqualTo(SignatureStatus.VALID);
		assertThat(verify(signature, revoked, true)).isEqualTo(SignatureStatus.KEY_REVOKED);
	}

	@Test
	void aKeyThatHadExpiredWhenItSignedIsReportedOnlyWhenTheCheckIsOn() throws Exception {
		byte[] signature = detachedSignature(TestPgpKeys.EXPIRED_PRIMARY, new Date());
		PgpPublicKeys keys = TestPgpKeys.EXPIRED_PRIMARY.publicKeys();

		assertThat(verify(signature, keys, false)).isEqualTo(SignatureStatus.VALID);
		assertThat(verify(signature, keys, true)).isEqualTo(SignatureStatus.KEY_EXPIRED);
	}

	@Test
	void aKeyThatExpiredAfterItSignedIsStillValid() throws Exception {
		// The key was made ten days ago and was good for one day, so a signature from
		// nine
		// and a half days ago was made while it was valid.
		Date whileValid = new Date(System.currentTimeMillis() - (19L * 12L * 3_600_000L));
		byte[] signature = detachedSignature(TestPgpKeys.EXPIRED_PRIMARY, whileValid);

		assertThat(verify(signature, TestPgpKeys.EXPIRED_PRIMARY.publicKeys(), true)).isEqualTo(SignatureStatus.VALID);
	}

}
