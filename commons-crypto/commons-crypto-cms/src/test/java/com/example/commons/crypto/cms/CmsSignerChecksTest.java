package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.SignatureStatus;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The opt-in checks on the signer's certificate: its validity when it signed, and the
 * caller's own trust decision.
 */
class CmsSignerChecksTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] TEXT = "Some text to sign".getBytes(StandardCharsets.UTF_8);

	private static final long DAY = 86_400_000L;

	/** A certificate for the key of the test key store that ran out yesterday. */
	private static X509Certificate expiredCertificate(PrivateKeyHandle key, X509Certificate original) throws Exception {
		X500Name name = new X500Name("CN=expired");
		long now = System.currentTimeMillis();
		return new JcaX509CertificateConverter().setProvider(BC)
			.getCertificate(new JcaX509v3CertificateBuilder(name, BigInteger.TEN, new Date(now - 10 * DAY),
					new Date(now - DAY), name, original.getPublicKey())
				.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(key.key())));
	}

	private static byte[] sign(PrivateKeyHandle key, X509Certificate certificate) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CmsSigner.create()
			.sign(SignRequest.builder().signer(key, certificate).mode(SignatureMode.DETACHED).build(),
					new ByteArrayInputStream(TEXT), out);
		return out.toByteArray();
	}

	private static SignatureStatus verify(byte[] signature, VerifyRequest request) {
		return CmsVerifier.create()
			.verifyDetached(request, new ByteArrayInputStream(TEXT), new ByteArrayInputStream(signature))
			.signatures()
			.get(0)
			.status();
	}

	@Test
	void aCertificateThatWasExpiredWhenItSignedIsReportedOnlyWhenTheCheckIsOn() throws Exception {
		X509Certificate original = TestKeys.certificate("rsa");
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			X509Certificate expired = expiredCertificate(key, original);
			byte[] signature = sign(key, expired);

			assertThat(verify(signature, VerifyRequest.builder().certificates(expired).build()))
				.isEqualTo(SignatureStatus.VALID);
			assertThat(verify(signature,
					VerifyRequest.builder().certificates(expired).checkCertificateValidity(true).build()))
				.isEqualTo(SignatureStatus.KEY_EXPIRED);
		}
	}

	@Test
	void aValidCertificateIsStillValidWhenTheCheckIsOn() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			byte[] signature = sign(key, certificate);

			assertThat(verify(signature,
					VerifyRequest.builder().certificates(certificate).checkCertificateValidity(true).build()))
				.isEqualTo(SignatureStatus.VALID);
		}
	}

	@Test
	void theCallersTrustCheckDecidesWhetherTheSignerIsTrusted() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			byte[] signature = sign(key, certificate);
			AtomicReference<X509Certificate> asked = new AtomicReference<>();

			assertThat(verify(signature, VerifyRequest.builder().certificates(certificate).trustCheck((candidate) -> {
				asked.set(candidate);
				return false;
			}).build())).isEqualTo(SignatureStatus.NOT_TRUSTED);
			assertThat(asked.get()).isEqualTo(certificate);
			assertThat(verify(signature,
					VerifyRequest.builder().certificates(certificate).trustCheck((candidate) -> true).build()))
				.isEqualTo(SignatureStatus.VALID);
		}
	}

}
