package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.SignatureResult;
import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgpSignVerifyTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] TEXT = "The quick brown fox jumps over the lazy dog. ".repeat(40)
		.getBytes(StandardCharsets.UTF_8);

	private final PgpSigner signer = PgpSigner.create();

	private final PgpVerifier verifier = PgpVerifier.create();

	private static byte[] randomBytes(int length) {
		byte[] data = new byte[length];
		new SecureRandom().nextBytes(data);
		return data;
	}

	private static PgpSecretKeys secret(TestPgpKeys.TestKey key) {
		return key.secretKeys(TestPgpKeys.PASSPHRASE);
	}

	private byte[] sign(byte[] data, SignRequest.Builder request) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.signer.sign(request.build(), new ByteArrayInputStream(data), out);
		return out.toByteArray();
	}

	private byte[] sign(byte[] data, SignatureMode mode, boolean armored, TestPgpKeys.TestKey... keys) {
		SignRequest.Builder request = SignRequest.builder().mode(mode).armored(armored);
		for (TestPgpKeys.TestKey key : keys) {
			request.signer(secret(key));
		}
		return sign(data, request);
	}

	private static VerifyRequest verifyRequest(boolean legacy, TestPgpKeys.TestKey... keys) {
		VerifyRequest.Builder request = VerifyRequest.builder().legacyCompatibility(legacy);
		for (TestPgpKeys.TestKey key : keys) {
			request.keys(key.publicKeys());
		}
		return request.build();
	}

	private VerifyResult verify(byte[] message, ByteArrayOutputStream content, TestPgpKeys.TestKey... keys) {
		return this.verifier.verify(verifyRequest(false, keys), new ByteArrayInputStream(message), content);
	}

	private VerifyResult verifyDetached(byte[] data, byte[] signature, TestPgpKeys.TestKey... keys) {
		return this.verifier.verifyDetached(verifyRequest(false, keys), new ByteArrayInputStream(data),
				new ByteArrayInputStream(signature));
	}

	@Test
	void embeddedSignatureRoundTripsForRsa() {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, TestPgpKeys.RSA);
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		VerifyResult result = verify(message, content, TestPgpKeys.RSA);

		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(TEXT);
		SignatureResult signature = result.signatures().get(0);
		assertThat(signature.signerId()).isEqualTo(String.format("%016X", TestPgpKeys.RSA.masterKeyId()));
		assertThat(signature.signingTime()).isBetween(Instant.now().minus(Duration.ofMinutes(5)),
				Instant.now().plus(Duration.ofMinutes(5)));
	}

	@Test
	void embeddedSignatureRoundTripsForEc() {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, TestPgpKeys.EC);

		assertThat(verify(message, new ByteArrayOutputStream(), TestPgpKeys.EC).allValid()).isTrue();
	}

	@Test
	void detachedSignatureRoundTripsAndDoesNotContainTheData() {
		byte[] signature = sign(TEXT, SignatureMode.DETACHED, false, TestPgpKeys.RSA);

		assertThat(new String(signature, StandardCharsets.ISO_8859_1))
			.doesNotContain(new String(TEXT, 0, 40, StandardCharsets.ISO_8859_1));
		assertThat(verifyDetached(TEXT, signature, TestPgpKeys.RSA).allValid()).isTrue();
	}

	@Test
	void largeDataIsStreamedBothWays() {
		byte[] data = randomBytes(400_000);

		byte[] embedded = sign(data, SignatureMode.EMBEDDED, false, TestPgpKeys.RSA);
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		assertThat(verify(embedded, content, TestPgpKeys.RSA).allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);

		byte[] detached = sign(data, SignatureMode.DETACHED, false, TestPgpKeys.EC);
		assertThat(verifyDetached(data, detached, TestPgpKeys.EC).allValid()).isTrue();
	}

	@Test
	void severalSignersEachGetTheirOwnResult() {
		byte[] embedded = sign(TEXT, SignatureMode.EMBEDDED, false, TestPgpKeys.RSA, TestPgpKeys.EC);
		byte[] detached = sign(TEXT, SignatureMode.DETACHED, false, TestPgpKeys.RSA, TestPgpKeys.EC);

		VerifyResult both = verify(embedded, new ByteArrayOutputStream(), TestPgpKeys.RSA, TestPgpKeys.EC);
		assertThat(both.signatures()).hasSize(2).allMatch((s) -> s.status() == SignatureStatus.VALID);
		assertThat(verifyDetached(TEXT, detached, TestPgpKeys.RSA, TestPgpKeys.EC).signatures()).hasSize(2)
			.allMatch((s) -> s.status() == SignatureStatus.VALID);

		VerifyResult onlyRsaKnown = verify(embedded, new ByteArrayOutputStream(), TestPgpKeys.RSA);
		assertThat(onlyRsaKnown.signatures()).extracting(SignatureResult::status)
			.containsExactlyInAnyOrder(SignatureStatus.VALID, SignatureStatus.UNKNOWN_SIGNER);
		assertThat(onlyRsaKnown.allValid()).isFalse();
	}

	@Test
	void aSignerWhoseKeyWasNotSuppliedIsUnknown() {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, TestPgpKeys.RSA);

		VerifyResult result = verify(message, new ByteArrayOutputStream(), TestPgpKeys.EC);

		assertThat(result.signatures()).extracting(SignatureResult::status)
			.containsExactly(SignatureStatus.UNKNOWN_SIGNER);
		assertThat(result.allValid()).isFalse();
	}

	@Test
	void changedDetachedDataGivesAnInvalidSignature() {
		byte[] signature = sign(TEXT, SignatureMode.DETACHED, false, TestPgpKeys.RSA);
		byte[] changed = TEXT.clone();
		changed[100] ^= 0x01;

		VerifyResult result = verifyDetached(changed, signature, TestPgpKeys.RSA);

		assertThat(result.signatures()).extracting(SignatureResult::status).containsExactly(SignatureStatus.INVALID);
	}

	@Test
	void changedEmbeddedContentGivesAnInvalidSignature() {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, TestPgpKeys.RSA);
		int index = new String(message, StandardCharsets.ISO_8859_1).indexOf("lazy dog");
		message[index] ^= 0x01;

		VerifyResult result = verify(message, new ByteArrayOutputStream(), TestPgpKeys.RSA);

		assertThat(result.signatures()).extracting(SignatureResult::status).containsExactly(SignatureStatus.INVALID);
	}

	@Test
	void armoredSignaturesAreVerifiedWithoutBeingToldTheFormat() {
		byte[] embedded = sign(TEXT, SignatureMode.EMBEDDED, true, TestPgpKeys.RSA);
		byte[] detached = sign(TEXT, SignatureMode.DETACHED, true, TestPgpKeys.RSA);

		assertThat(new String(embedded, StandardCharsets.US_ASCII)).startsWith("-----BEGIN PGP MESSAGE-----");
		assertThat(new String(detached, StandardCharsets.US_ASCII)).startsWith("-----BEGIN PGP SIGNATURE-----");
		assertThat(verify(embedded, new ByteArrayOutputStream(), TestPgpKeys.RSA).allValid()).isTrue();
		assertThat(verifyDetached(TEXT, detached, TestPgpKeys.RSA).allValid()).isTrue();
	}

	@Test
	void cleartextSignaturesKeepTheTextReadableAndVerify() {
		String text = "first line\n- a line that starts with a dash\n-----not a header\nthird line with spaces   \nlast";
		byte[] message = sign(text.getBytes(StandardCharsets.UTF_8),
				SignRequest.builder().cleartext().signer(secret(TestPgpKeys.RSA)));

		String armored = new String(message, StandardCharsets.UTF_8);
		assertThat(armored).startsWith("-----BEGIN PGP SIGNED MESSAGE-----")
			.contains("first line\n")
			.contains("\n- - a line that starts with a dash\n")
			.contains("\n-----BEGIN PGP SIGNATURE-----");
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = verify(message, content, TestPgpKeys.RSA);
		assertThat(result.allValid()).isTrue();
		assertThat(content.toString(StandardCharsets.UTF_8)).isEqualTo(text);
	}

	@Test
	void cleartextSignaturesNormaliseLineEndings() {
		byte[] message = sign("one\r\ntwo\r\n".getBytes(StandardCharsets.UTF_8),
				SignRequest.builder().cleartext().signer(secret(TestPgpKeys.EC)));

		ByteArrayOutputStream content = new ByteArrayOutputStream();
		assertThat(verify(message, content, TestPgpKeys.EC).allValid()).isTrue();
		assertThat(content.toString(StandardCharsets.UTF_8)).isEqualTo("one\ntwo");
	}

	@Test
	void changedCleartextGivesAnInvalidSignature() {
		byte[] message = sign("pay alice 10".getBytes(StandardCharsets.UTF_8),
				SignRequest.builder().cleartext().signer(secret(TestPgpKeys.RSA)));
		String changed = new String(message, StandardCharsets.UTF_8).replace("alice 10", "alice 99");

		VerifyResult result = verify(changed.getBytes(StandardCharsets.UTF_8), new ByteArrayOutputStream(),
				TestPgpKeys.RSA);

		assertThat(result.signatures()).extracting(SignatureResult::status).containsExactly(SignatureStatus.INVALID);
	}

	@Test
	void aDetachedSignatureHasNoContentToVerifyAsEmbedded() {
		byte[] detached = sign(TEXT, SignatureMode.DETACHED, false, TestPgpKeys.RSA);

		assertThatThrownBy(() -> verify(detached, new ByteArrayOutputStream(), TestPgpKeys.RSA))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("detached");
	}

	@Test
	void garbageIsACryptoException() {
		assertThatThrownBy(() -> verify(randomBytes(200), new ByteArrayOutputStream(), TestPgpKeys.RSA))
			.isInstanceOf(CryptoException.class);
		assertThatThrownBy(() -> verifyDetached(TEXT, randomBytes(200), TestPgpKeys.RSA))
			.isInstanceOf(CryptoException.class);
	}

	@Test
	void aWrongPassphraseIsReportedAsSuch() {
		SignRequest.Builder request = SignRequest.builder()
			.signer(TestPgpKeys.RSA.secretKeys("not-the-passphrase".toCharArray()));

		assertThatThrownBy(() -> sign(TEXT, request)).isInstanceOf(PinRequiredException.class);
	}

	@Test
	void sha1SignaturesAreRefusedUnlessLegacyCompatibilityIsSet() throws Exception {
		PGPSecretKeyRing ring = new JcaPGPSecretKeyRingCollection(PGPUtil.getDecoderStream(
				new ByteArrayInputStream(TestPgpKeys.RSA.secretArmored().getBytes(StandardCharsets.US_ASCII))))
			.getKeyRings()
			.next();
		PGPPrivateKey privateKey = ring.getSecretKey()
			.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC).build())
				.setProvider(BC)
				.build(TestPgpKeys.PASSPHRASE));
		PGPSignatureGenerator generator = new PGPSignatureGenerator(
				new JcaPGPContentSignerBuilder(ring.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1)
					.setProvider(BC),
				ring.getPublicKey());
		generator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
		generator.update(TEXT);
		ByteArrayOutputStream signature = new ByteArrayOutputStream();
		BCPGOutputStream out = new BCPGOutputStream(signature);
		generator.generate().encode(out);
		out.close();
		byte[] sha1Signature = signature.toByteArray();

		assertThatThrownBy(() -> verifyDetached(TEXT, sha1Signature, TestPgpKeys.RSA))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("legacy");
		VerifyResult result = this.verifier.verifyDetached(verifyRequest(true, TestPgpKeys.RSA),
				new ByteArrayInputStream(TEXT), new ByteArrayInputStream(sha1Signature));
		assertThat(result.allValid()).isTrue();
	}

	@Test
	void aSignRequestNeedsAtLeastOneSigner() {
		assertThatThrownBy(() -> SignRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aVerifyRequestNeedsAtLeastOneKey() {
		assertThatThrownBy(() -> VerifyRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
