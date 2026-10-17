package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.SignatureResult;
import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsSignVerifyTest {

	/**
	 * Text that is not a message. Random bytes would sometimes happen to parse as a valid
	 * packet.
	 */
	private static final byte[] NOT_A_MESSAGE = "This is only text, and no encrypted or signed message. ".repeat(4)
		.getBytes(StandardCharsets.UTF_8);

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] TEXT = "The quick brown fox jumps over the lazy dog. ".repeat(40)
		.getBytes(StandardCharsets.UTF_8);

	private final CmsSigner signer = CmsSigner.create();

	private final CmsVerifier verifier = CmsVerifier.create();

	private static byte[] randomBytes(int length) {
		byte[] data = new byte[length];
		new SecureRandom().nextBytes(data);
		return data;
	}

	private byte[] sign(byte[] data, SignatureMode mode, boolean armored, String... keyNames) throws Exception {
		SignRequest.Builder request = SignRequest.builder().mode(mode).armored(armored);
		for (String name : keyNames) {
			request.signer(TestKeys.handle(name), TestKeys.certificate(name));
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.signer.sign(request.build(), new ByteArrayInputStream(data), out);
		return out.toByteArray();
	}

	private static VerifyRequest verifyRequest(boolean legacy, String... certificateNames) throws Exception {
		VerifyRequest.Builder request = VerifyRequest.builder().legacyCompatibility(legacy);
		for (String name : certificateNames) {
			request.certificates(TestKeys.certificate(name));
		}
		return request.build();
	}

	private VerifyResult verifyEmbedded(byte[] message, ByteArrayOutputStream content, String... certificateNames)
			throws Exception {
		return this.verifier.verify(verifyRequest(false, certificateNames), new ByteArrayInputStream(message), content);
	}

	private VerifyResult verifyDetached(byte[] data, byte[] signature, String... certificateNames) throws Exception {
		return this.verifier.verifyDetached(verifyRequest(false, certificateNames), new ByteArrayInputStream(data),
				new ByteArrayInputStream(signature));
	}

	@Test
	void embeddedSignatureRoundTripsForRsa() throws Exception {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, "rsa");
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		VerifyResult result = verifyEmbedded(message, content, "rsa");

		assertThat(result.allValid()).isTrue();
		assertThat(result.signatures()).hasSize(1);
		assertThat(content.toByteArray()).isEqualTo(TEXT);
		SignatureResult signature = result.signatures().get(0);
		assertThat(signature.signerId()).isNotBlank();
		assertThat(signature.signingTime()).isBetween(Instant.now().minus(Duration.ofMinutes(5)),
				Instant.now().plus(Duration.ofMinutes(5)));
	}

	@Test
	void embeddedSignatureRoundTripsForEc() throws Exception {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, "ec");

		assertThat(verifyEmbedded(message, new ByteArrayOutputStream(), "ec").allValid()).isTrue();
	}

	@Test
	void detachedSignatureRoundTripsAndDoesNotContainTheData() throws Exception {
		byte[] signature = sign(TEXT, SignatureMode.DETACHED, false, "rsa");

		assertThat(new String(signature, StandardCharsets.ISO_8859_1))
			.doesNotContain(new String(TEXT, 0, 40, StandardCharsets.ISO_8859_1));
		assertThat(verifyDetached(TEXT, signature, "rsa").allValid()).isTrue();
	}

	@Test
	void largeDataIsStreamedBothWays() throws Exception {
		byte[] data = randomBytes(400_000);

		byte[] embedded = sign(data, SignatureMode.EMBEDDED, false, "rsa");
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		assertThat(verifyEmbedded(embedded, content, "rsa").allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);

		byte[] detached = sign(data, SignatureMode.DETACHED, false, "ec");
		assertThat(verifyDetached(data, detached, "ec").allValid()).isTrue();
	}

	@Test
	void severalSignersEachGetTheirOwnResult() throws Exception {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, "rsa", "ec");

		VerifyResult both = verifyEmbedded(message, new ByteArrayOutputStream(), "rsa", "ec");
		assertThat(both.signatures()).hasSize(2).allMatch((s) -> s.status() == SignatureStatus.VALID);

		VerifyResult onlyRsaKnown = verifyEmbedded(message, new ByteArrayOutputStream(), "rsa");
		assertThat(onlyRsaKnown.signatures()).extracting(SignatureResult::status)
			.containsExactlyInAnyOrder(SignatureStatus.VALID, SignatureStatus.UNKNOWN_SIGNER);
		assertThat(onlyRsaKnown.allValid()).isFalse();
	}

	@Test
	void aSignerWhoseCertificateWasNotSuppliedIsUnknown() throws Exception {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, "rsa");

		VerifyResult result = verifyEmbedded(message, new ByteArrayOutputStream(), "ec");

		assertThat(result.signatures()).extracting(SignatureResult::status)
			.containsExactly(SignatureStatus.UNKNOWN_SIGNER);
		assertThat(result.allValid()).isFalse();
	}

	@Test
	void changedDetachedDataGivesAnInvalidSignature() throws Exception {
		byte[] signature = sign(TEXT, SignatureMode.DETACHED, false, "rsa");
		byte[] changed = TEXT.clone();
		changed[100] ^= 0x01;

		VerifyResult result = verifyDetached(changed, signature, "rsa");

		assertThat(result.signatures()).extracting(SignatureResult::status).containsExactly(SignatureStatus.INVALID);
	}

	@Test
	void changedEmbeddedContentGivesAnInvalidSignature() throws Exception {
		byte[] message = sign(TEXT, SignatureMode.EMBEDDED, false, "rsa");
		int index = new String(message, StandardCharsets.ISO_8859_1).indexOf("lazy dog");
		message[index] ^= 0x01;

		VerifyResult result = verifyEmbedded(message, new ByteArrayOutputStream(), "rsa");

		assertThat(result.signatures()).extracting(SignatureResult::status).containsExactly(SignatureStatus.INVALID);
	}

	@Test
	void armoredSignaturesAreVerifiedWithoutBeingToldTheFormat() throws Exception {
		byte[] embedded = sign(TEXT, SignatureMode.EMBEDDED, true, "rsa");
		byte[] detached = sign(TEXT, SignatureMode.DETACHED, true, "rsa");

		assertThat(new String(embedded, StandardCharsets.US_ASCII)).startsWith("-----BEGIN CMS-----\n")
			.endsWith("-----END CMS-----\n");
		assertThat(verifyEmbedded(embedded, new ByteArrayOutputStream(), "rsa").allValid()).isTrue();
		assertThat(verifyDetached(TEXT, detached, "rsa").allValid()).isTrue();
	}

	@Test
	void aDetachedSignatureHasNoContentToVerifyAsEmbedded() throws Exception {
		byte[] detached = sign(TEXT, SignatureMode.DETACHED, false, "rsa");

		assertThatThrownBy(() -> verifyEmbedded(detached, new ByteArrayOutputStream(), "rsa"))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("detached");
	}

	@Test
	void garbageIsACryptoException() {
		assertThatThrownBy(() -> verifyEmbedded(NOT_A_MESSAGE, new ByteArrayOutputStream(), "rsa"))
			.isInstanceOf(CryptoException.class);
		assertThatThrownBy(() -> verifyDetached(TEXT, NOT_A_MESSAGE, "rsa")).isInstanceOf(CryptoException.class);
	}

	@Test
	void sha1SignaturesAreRefusedUnlessLegacyCompatibilityIsSet() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		byte[] message;
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
			generator.addSignerInfoGenerator(
					new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider(BC).build())
						.build(new JcaContentSignerBuilder("SHA1withRSA").setProvider(BC).build(key.key()),
								certificate));
			generator.addCertificates(new JcaCertStore(List.of(certificate)));
			CMSSignedData signed = generator.generate(new CMSProcessableByteArray(TEXT), true);
			message = signed.getEncoded();
		}
		byte[] sha1Message = message;

		assertThatThrownBy(() -> this.verifier.verify(verifyRequest(false, "rsa"),
				new ByteArrayInputStream(sha1Message), new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("legacy");
		VerifyResult result = this.verifier.verify(verifyRequest(true, "rsa"), new ByteArrayInputStream(sha1Message),
				new ByteArrayOutputStream());
		assertThat(result.allValid()).isTrue();
	}

	@Test
	void aSignRequestNeedsAtLeastOneSigner() {
		assertThatThrownBy(() -> SignRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aVerifyRequestNeedsAtLeastOneCertificate() {
		assertThatThrownBy(() -> VerifyRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
