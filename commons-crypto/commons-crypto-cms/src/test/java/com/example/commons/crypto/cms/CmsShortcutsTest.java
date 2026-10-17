package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CmsShortcutsTest {

	private static final byte[] DATA = "A short message".getBytes(StandardCharsets.UTF_8);

	@Test
	void encryptsAndDecrypts() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();

		Cms.encrypt(new ByteArrayInputStream(DATA), encrypted, certificate);

		ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			Cms.decrypt(new ByteArrayInputStream(encrypted.toByteArray()), decrypted, key, certificate);
		}
		assertThat(decrypted.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void signsAndVerifiesWithTheSignatureEmbedded() throws Exception {
		X509Certificate certificate = TestKeys.certificate("ec");
		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("ec")) {
			Cms.sign(new ByteArrayInputStream(DATA), signed, key, certificate);
		}

		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = Cms.verify(new ByteArrayInputStream(signed.toByteArray()), content, certificate);

		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void signsAndVerifiesWithTheSignatureDetached() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		ByteArrayOutputStream signature = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			Cms.signDetached(new ByteArrayInputStream(DATA), signature, key, certificate);
		}

		VerifyResult result = Cms.verifyDetached(new ByteArrayInputStream(DATA),
				new ByteArrayInputStream(signature.toByteArray()), certificate);

		assertThat(result.allValid()).isTrue();
	}

}
