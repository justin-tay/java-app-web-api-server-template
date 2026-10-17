package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * OpenPGP always wraps the session key for an RSA key with PKCS#1 v1.5, so a bad padding
 * must look like any other damage to the message (Bleichenbacher's attack).
 */
class PgpPkcs1OracleTest {

	private static final byte[] DATA = "A short message".repeat(50).getBytes(StandardCharsets.UTF_8);

	private static Throwable failure(byte[] message) {
		return catchThrowable(() -> PgpDecryptor.create()
			.decrypt(DecryptRequest.builder().keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE)).build(),
					new ByteArrayInputStream(message), new ByteArrayOutputStream()));
	}

	private static byte[] message() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), out, TestPgpKeys.RSA.publicKeys());
		return out.toByteArray();
	}

	@Test
	void aDamagedSessionKeyFailsLikeDamagedContent() {
		byte[] damagedKey = message();
		// The encrypted session key follows the packet header, the version, the key ID
		// and the
		// algorithm and the MPI length: about 15 bytes in.
		damagedKey[100] ^= 1;
		byte[] damagedContent = message();
		damagedContent[damagedContent.length - 20] ^= 1;

		Throwable fromKey = failure(damagedKey);
		Throwable fromContent = failure(damagedContent);

		assertThat(fromKey).isNotNull();
		assertThat(fromKey.getClass()).isEqualTo(fromContent.getClass());
		assertThat(fromKey.getMessage()).isEqualTo(fromContent.getMessage());
	}

}
