package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.SignatureResult;
import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsCombinedTest {

	private final CmsEncryptor encryptor = CmsEncryptor.create();

	private final CmsDecryptor decryptor = CmsDecryptor.create();

	private static byte[] randomBytes(int length) {
		byte[] data = new byte[length];
		new SecureRandom().nextBytes(data);
		return data;
	}

	private byte[] signAndEncrypt(byte[] data, boolean armored, String[] signers, String... recipients)
			throws Exception {
		SignRequest.Builder sign = SignRequest.builder();
		for (String signer : signers) {
			sign.signer(TestKeys.handle(signer), TestKeys.certificate(signer));
		}
		EncryptRequest.Builder encrypt = EncryptRequest.builder().armored(armored);
		for (String recipient : recipients) {
			encrypt.recipients(CmsRecipient.certificate(TestKeys.certificate(recipient)));
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.encryptor.signAndEncrypt(sign.build(), encrypt.build(), new ByteArrayInputStream(data), out);
		return out.toByteArray();
	}

	private VerifyResult decryptAndVerify(byte[] message, ByteArrayOutputStream content, String recipient,
			String... signers) throws Exception {
		VerifyRequest.Builder verify = VerifyRequest.builder();
		for (String signer : signers) {
			verify.certificates(TestKeys.certificate(signer));
		}
		DecryptRequest decrypt = DecryptRequest.builder()
			.key(TestKeys.handle(recipient), TestKeys.certificate(recipient))
			.build();
		return this.decryptor.decryptAndVerify(decrypt, verify.build(), new ByteArrayInputStream(message), content);
	}

	@Test
	void signAndEncryptThenDecryptAndVerifyForRsa() throws Exception {
		byte[] data = randomBytes(300_000);

		byte[] message = signAndEncrypt(data, false, new String[] { "rsa" }, "rsa");
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = decryptAndVerify(message, content, "rsa", "rsa");

		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void signAndEncryptThenDecryptAndVerifyForEc() throws Exception {
		byte[] data = "hello elliptic curve".getBytes(StandardCharsets.UTF_8);

		byte[] message = signAndEncrypt(data, false, new String[] { "ec" }, "ec");
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		assertThat(decryptAndVerify(message, content, "ec", "ec").allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void theSignerAndTheRecipientCanBeDifferentKeys() throws Exception {
		byte[] data = randomBytes(2000);

		byte[] message = signAndEncrypt(data, false, new String[] { "rsa" }, "ec");
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		assertThat(decryptAndVerify(message, content, "ec", "rsa").allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void severalSignersAndRecipients() throws Exception {
		byte[] data = randomBytes(2000);

		byte[] message = signAndEncrypt(data, false, new String[] { "rsa", "ec" }, "rsa", "ec");

		VerifyResult viaRsa = decryptAndVerify(message, new ByteArrayOutputStream(), "rsa", "rsa", "ec");
		VerifyResult viaEc = decryptAndVerify(message, new ByteArrayOutputStream(), "ec", "rsa", "ec");
		assertThat(viaRsa.signatures()).hasSize(2).allMatch((s) -> s.status() == SignatureStatus.VALID);
		assertThat(viaEc.signatures()).hasSize(2).allMatch((s) -> s.status() == SignatureStatus.VALID);
	}

	@Test
	void aSignerWhoseCertificateWasNotSuppliedIsUnknown() throws Exception {
		byte[] message = signAndEncrypt(randomBytes(100), false, new String[] { "rsa" }, "ec");

		VerifyResult result = decryptAndVerify(message, new ByteArrayOutputStream(), "ec", "ec");

		assertThat(result.signatures()).extracting(SignatureResult::status)
			.containsExactly(SignatureStatus.UNKNOWN_SIGNER);
	}

	@Test
	void armoredOutputIsPem() throws Exception {
		byte[] data = randomBytes(3000);

		byte[] message = signAndEncrypt(data, true, new String[] { "rsa" }, "rsa");
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		assertThat(new String(message, StandardCharsets.US_ASCII)).startsWith("-----BEGIN CMS-----\n");
		assertThat(decryptAndVerify(message, content, "rsa", "rsa").allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void theDataIsNotVisibleInTheMessage() throws Exception {
		byte[] data = "The quick brown fox jumps over the lazy dog. ".repeat(20).getBytes(StandardCharsets.UTF_8);

		byte[] message = signAndEncrypt(data, false, new String[] { "rsa" }, "rsa");

		assertThat(new String(message, StandardCharsets.ISO_8859_1)).doesNotContain("lazy dog");
	}

	@Test
	void aKeyThatIsNotARecipientIsRejected() throws Exception {
		byte[] message = signAndEncrypt(randomBytes(100), false, new String[] { "rsa" }, "rsa");

		assertThatThrownBy(() -> decryptAndVerify(message, new ByteArrayOutputStream(), "ec", "rsa"))
			.isInstanceOf(NoMatchingKeyException.class);
	}

	@Test
	void aTamperedMessageIsAnIntegrityFailure() throws Exception {
		byte[] message = signAndEncrypt(randomBytes(2000), false, new String[] { "rsa" }, "rsa");
		// The message ends with the 16 byte tag followed by three pairs of
		// end-of-contents octets.
		message[message.length - 10] ^= 0x01;

		assertThatThrownBy(() -> decryptAndVerify(message, new ByteArrayOutputStream(), "rsa", "rsa"))
			.isInstanceOf(IntegrityException.class);
	}

	@Test
	void aMessageThatIsEncryptedButNotSignedIsACryptoException() throws Exception {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		this.encryptor.encrypt(
				EncryptRequest.builder().recipients(CmsRecipient.certificate(TestKeys.certificate("rsa"))).build(),
				new ByteArrayInputStream(randomBytes(500)), encrypted);

		assertThatThrownBy(() -> decryptAndVerify(encrypted.toByteArray(), new ByteArrayOutputStream(), "rsa", "rsa"))
			.isInstanceOf(CryptoException.class);
	}

	@Test
	void aDetachedSignatureCannotBeCombinedWithEncryption() throws Exception {
		SignRequest sign = SignRequest.builder()
			.signer(TestKeys.handle("rsa"), TestKeys.certificate("rsa"))
			.mode(SignatureMode.DETACHED)
			.build();
		EncryptRequest encrypt = EncryptRequest.builder()
			.recipients(CmsRecipient.certificate(TestKeys.certificate("rsa")))
			.build();

		assertThatThrownBy(() -> this.encryptor.signAndEncrypt(sign, encrypt, new ByteArrayInputStream(new byte[1]),
				new ByteArrayOutputStream()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theCallersStreamsAreLeftOpen() throws Exception {
		boolean[] closed = new boolean[2];
		InputStream data = new FilterInputStream(new ByteArrayInputStream(randomBytes(500))) {
			@Override
			public void close() throws IOException {
				closed[0] = true;
				super.close();
			}
		};
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		OutputStream out = new FilterOutputStream(bytes) {
			@Override
			public void close() throws IOException {
				closed[1] = true;
				super.close();
			}
		};
		this.encryptor.signAndEncrypt(
				SignRequest.builder().signer(TestKeys.handle("rsa"), TestKeys.certificate("rsa")).build(),
				EncryptRequest.builder().recipients(CmsRecipient.certificate(TestKeys.certificate("rsa"))).build(),
				data, out);

		assertThat(closed).containsExactly(false, false);
	}

	@Test
	void decryptingLeavesTheCallersInputOpen() throws Exception {
		boolean[] closed = new boolean[1];
		byte[] message = signAndEncrypt(randomBytes(500), false, new String[] { "rsa" }, "rsa");
		InputStream in = new FilterInputStream(new ByteArrayInputStream(message)) {
			@Override
			public void close() throws IOException {
				closed[0] = true;
				super.close();
			}
		};
		X509Certificate certificate = TestKeys.certificate("rsa");
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			this.decryptor.decrypt(DecryptRequest.builder().key(key, certificate).build(), in,
					new ByteArrayOutputStream());
		}

		assertThat(closed[0]).isFalse();
	}

}
