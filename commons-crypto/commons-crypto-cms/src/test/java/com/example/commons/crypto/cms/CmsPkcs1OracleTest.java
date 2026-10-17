package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PrivateKeyHandle;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputAEADEncryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RSA PKCS#1 v1.5 key transport, which only legacy compatibility (or a token handle that
 * allows it) accepts, must not tell a sender why a message failed: a bad padding must
 * look like any other failure (Bleichenbacher's attack).
 */
class CmsPkcs1OracleTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "A short message".getBytes(StandardCharsets.UTF_8);

	/** An AuthEnvelopedData message whose content key is wrapped with PKCS#1 v1.5. */
	private static byte[] message(X509Certificate certificate) throws Exception {
		CMSAuthEnvelopedDataGenerator generator = new CMSAuthEnvelopedDataGenerator();
		generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(certificate).setProvider(BC));
		CMSAuthEnvelopedData data = generator.generate(new CMSProcessableByteArray(DATA),
				(OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM).setProvider(BC)
					.build());
		return data.getEncoded();
	}

	private static Throwable failure(byte[] message, PrivateKeyHandle key, X509Certificate certificate) {
		return org.assertj.core.api.Assertions.catchThrowable(() -> CmsDecryptor.create()
			.decrypt(DecryptRequest.builder().key(key, certificate).legacyCompatibility(true).build(),
					new ByteArrayInputStream(message), new ByteArrayOutputStream()));
	}

	@Test
	void anUntouchedMessageIsDecrypted() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, certificate).legacyCompatibility(true).build(),
						new ByteArrayInputStream(message(certificate)), out);
		}

		assertThat(out.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void aDamagedWrappedKeyFailsLikeDamagedContent() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		byte[] damagedKey = message(certificate);
		// The wrapped key is the OCTET STRING of 256 bytes: 04 82 01 00.
		int at = indexOf(damagedKey, new byte[] { 0x04, (byte) 0x82, 0x01, 0x00 }) + 4;
		damagedKey[at + 100] ^= 1;
		byte[] damagedContent = message(certificate);
		damagedContent[damagedContent.length - 20] ^= 1;

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			Throwable fromKey = failure(damagedKey, key, certificate);
			Throwable fromContent = failure(damagedContent, key, certificate);

			assertThat(fromKey).isInstanceOf(CryptoException.class);
			assertThat(fromKey.getClass()).isEqualTo(fromContent.getClass());
			assertThat(fromKey.getMessage()).isEqualTo(fromContent.getMessage());
		}
	}

	@Test
	void aWrappedKeyOfTheWrongKindFailsLikeAnyOther() throws Exception {
		// Bytes that cannot be PKCS#1 v1.5 padding at all: the wrapped key is all zeros.
		X509Certificate certificate = TestKeys.certificate("rsa");
		byte[] zeroed = message(certificate);
		int at = indexOf(zeroed, new byte[] { 0x04, (byte) 0x82, 0x01, 0x00 }) + 4;
		java.util.Arrays.fill(zeroed, at, at + 256, (byte) 0);
		byte[] damagedContent = message(certificate);
		damagedContent[damagedContent.length - 20] ^= 1;

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			Throwable fromKey = failure(zeroed, key, certificate);
			Throwable fromContent = failure(damagedContent, key, certificate);

			assertThat(fromKey.getClass()).isEqualTo(fromContent.getClass());
			assertThat(fromKey.getMessage()).isEqualTo(fromContent.getMessage());
		}
	}

	private static int indexOf(byte[] data, byte[] pattern) {
		for (int i = 0; i + pattern.length <= data.length; i++) {
			if (java.util.Arrays.equals(data, i, i + pattern.length, pattern, 0, pattern.length)) {
				return i;
			}
		}
		throw new AssertionError("pattern not found");
	}

}
