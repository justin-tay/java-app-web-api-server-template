package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PrivateKeyHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmorLimitsTest {

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.US_ASCII);
	}

	private static void decrypt(byte[] message) throws Exception {
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, TestKeys.certificate("rsa")).build(),
						new ByteArrayInputStream(message), new ByteArrayOutputStream());
		}
	}

	@Test
	void aBodyLineThatNeverEndsIsRefused() {
		byte[] message = bytes("-----BEGIN CMS-----\n" + "A".repeat(100_000) + "\n-----END CMS-----\n");

		assertThatThrownBy(() -> decrypt(message)).isInstanceOf(CryptoException.class);
	}

	@Test
	void aHeaderLineThatNeverEndsIsRefused() {
		byte[] message = bytes("-----BEGIN " + "X".repeat(100_000));

		assertThatThrownBy(() -> decrypt(message)).isInstanceOf(CryptoException.class);
	}

	@Test
	void anOrdinaryArmoredMessageStillWorks() throws Exception {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Cms.encrypt(new ByteArrayInputStream(bytes("fine")), encrypted, TestKeys.certificate("rsa"));
		ByteArrayOutputStream armored = new ByteArrayOutputStream();
		CmsEncryptor.create()
			.encrypt(EncryptRequest.builder()
				.recipients(CmsRecipient.certificate(TestKeys.certificate("rsa")))
				.armored(true)
				.build(), new ByteArrayInputStream(bytes("fine")), armored);

		decrypt(armored.toByteArray());
	}

}
