package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgpKeysLifecycleTest {

	private static final byte[] DATA = "keys that can be closed".getBytes(StandardCharsets.UTF_8);

	@Test
	void secretKeysWorkInsideTryWithResources() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), encrypted, TestPgpKeys.RSA.publicKeys());

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		try (PgpSecretKeys keys = TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE)) {
			Pgp.decrypt(new ByteArrayInputStream(encrypted.toByteArray()), plain, keys);
		}

		assertThat(plain.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void closedKeysCannotBeUsed() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), encrypted, TestPgpKeys.RSA.publicKeys());
		PgpSecretKeys keys = TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE);

		keys.close();
		keys.close();

		assertThatThrownBy(
				() -> Pgp.decrypt(new ByteArrayInputStream(encrypted.toByteArray()), new ByteArrayOutputStream(), keys))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("closed");
		assertThatThrownBy(() -> Pgp.sign(new ByteArrayInputStream(DATA), new ByteArrayOutputStream(), keys))
			.isInstanceOf(IllegalStateException.class);
	}

}
