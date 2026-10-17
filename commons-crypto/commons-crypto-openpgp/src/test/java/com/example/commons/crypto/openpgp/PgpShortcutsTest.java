package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	void decryptsToBytesAndReturnsNothingForAnAlteredMessage() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), encrypted, TestPgpKeys.RSA.publicKeys());
		byte[] message = encrypted.toByteArray();
		PgpSecretKeys keys = TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE);

		assertThat(Pgp.decryptToBytes(new ByteArrayInputStream(message), keys, 1024)).isEqualTo(DATA);

		message[message.length - 10] ^= 1;
		assertThatThrownBy(() -> Pgp.decryptToBytes(new ByteArrayInputStream(message), keys, 1024))
			.isInstanceOf(CryptoException.class);
	}

	@Test
	void decryptsToBytesOnlyUpToTheLimit() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), encrypted, TestPgpKeys.RSA.publicKeys());

		assertThatThrownBy(() -> Pgp.decryptToBytes(new ByteArrayInputStream(encrypted.toByteArray()),
				TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE), DATA.length - 1))
			.isInstanceOf(CryptoException.class);
	}

}
