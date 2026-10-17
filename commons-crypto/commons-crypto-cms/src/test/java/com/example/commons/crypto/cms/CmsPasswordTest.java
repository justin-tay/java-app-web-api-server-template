package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.PrivateKeyHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Messages encrypted to a password (the CMS password recipient, RFC 3211), alone or next
 * to certificates.
 */
class CmsPasswordTest {

	private static final byte[] DATA = "A short message".getBytes(StandardCharsets.UTF_8);

	private static final char[] PASSWORD = "correct horse battery staple".toCharArray();

	private static byte[] encrypt(EncryptRequest.Builder request) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CmsEncryptor.create().encrypt(request.build(), new ByteArrayInputStream(DATA), out);
		return out.toByteArray();
	}

	private static byte[] decrypt(byte[] message, DecryptRequest.Builder request) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CmsDecryptor.create().decrypt(request.build(), new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	private static int indexOf(byte[] data, byte[] pattern) {
		for (int i = 0; i + pattern.length <= data.length; i++) {
			if (Arrays.equals(data, i, i + pattern.length, pattern, 0, pattern.length)) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void aPasswordMessageRoundTrips() {
		byte[] message = encrypt(EncryptRequest.builder().password(PASSWORD));

		assertThat(decrypt(message, DecryptRequest.builder().password(PASSWORD))).isEqualTo(DATA);
	}

	@Test
	void aPasswordMessageRoundTripsInPem() {
		byte[] message = encrypt(EncryptRequest.builder().password(PASSWORD).armored(true));

		assertThat(new String(message, StandardCharsets.US_ASCII)).contains("BEGIN CMS");
		assertThat(decrypt(message, DecryptRequest.builder().password(PASSWORD))).isEqualTo(DATA);
	}

	@Test
	void aWrongPasswordIsReportedAsOne() {
		byte[] message = encrypt(EncryptRequest.builder().password(PASSWORD));

		assertThatThrownBy(() -> decrypt(message, DecryptRequest.builder().password("not it".toCharArray())))
			.isInstanceOf(PinRequiredException.class);
	}

	@Test
	void aPasswordMessageHasNoMatchingKeyForACertificateAlone() throws Exception {
		byte[] message = encrypt(EncryptRequest.builder().password(PASSWORD));

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> decrypt(message, DecryptRequest.builder().key(key, TestKeys.certificate("rsa"))))
				.isInstanceOf(NoMatchingKeyException.class);
		}
	}

	@Test
	void aMessageForACertificateAndAPasswordOpensWithEither() throws Exception {
		X509Certificate certificate = TestKeys.certificate("ec");
		byte[] message = encrypt(
				EncryptRequest.builder().recipients(CmsRecipient.certificate(certificate)).password(PASSWORD));

		assertThat(decrypt(message, DecryptRequest.builder().password(PASSWORD))).isEqualTo(DATA);
		try (PrivateKeyHandle key = TestKeys.handle("ec")) {
			assertThat(decrypt(message, DecryptRequest.builder().key(key, certificate))).isEqualTo(DATA);
		}
	}

	@Test
	void aMessageWithAnAbsurdIterationCountIsRefused() {
		// A sender could make opening a message take forever. 100000 is the INTEGER 01 86
		// A0, and
		// it is changed to 7F FF FF, which is more than 8 million.
		byte[] message = encrypt(EncryptRequest.builder().password(PASSWORD).passwordIterations(100_000));
		byte[] iterations = { 0x02, 0x03, 0x01, (byte) 0x86, (byte) 0xA0 };
		int at = indexOf(message, iterations);
		assertThat(at).isPositive();
		message[at + 2] = 0x7F;
		message[at + 3] = (byte) 0xFF;
		message[at + 4] = (byte) 0xFF;

		assertThatThrownBy(() -> decrypt(message, DecryptRequest.builder().password(PASSWORD)))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("iteration");
	}

	@Test
	void anEncryptRequestNeedsARecipientOrAPassword() {
		assertThatThrownBy(() -> EncryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aDecryptRequestNeedsAKeyOrAPassword() {
		assertThatThrownBy(() -> DecryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
