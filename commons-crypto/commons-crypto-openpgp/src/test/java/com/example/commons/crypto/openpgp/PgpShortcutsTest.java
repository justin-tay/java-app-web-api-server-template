package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgpShortcutsTest {

	private static final byte[] DATA = "A short message".getBytes(StandardCharsets.UTF_8);

	@Test
	void encryptsAndDecrypts() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();

		Pgp.encrypt(new ByteArrayInputStream(DATA), encrypted, TestPgpKeys.RSA.publicKeys());

		ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
		Pgp.decrypt(new ByteArrayInputStream(encrypted.toByteArray()), decrypted,
				TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE));
		assertThat(decrypted.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void signsAndVerifiesWithTheSignatureEmbedded() {
		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		Pgp.sign(new ByteArrayInputStream(DATA), signed, TestPgpKeys.EC.secretKeys(TestPgpKeys.PASSPHRASE));

		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = Pgp.verify(new ByteArrayInputStream(signed.toByteArray()), content,
				TestPgpKeys.EC.publicKeys());

		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void signsAndVerifiesWithTheSignatureDetached() {
		ByteArrayOutputStream signature = new ByteArrayOutputStream();
		Pgp.signDetached(new ByteArrayInputStream(DATA), signature, TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE));

		VerifyResult result = Pgp.verifyDetached(new ByteArrayInputStream(DATA),
				new ByteArrayInputStream(signature.toByteArray()), TestPgpKeys.RSA.publicKeys());

		assertThat(result.allValid()).isTrue();
	}

}
