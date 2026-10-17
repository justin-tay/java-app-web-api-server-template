package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.SignatureResult;
import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgpCombinedTest {

	private final PgpEncryptor encryptor = PgpEncryptor.create();

	private final PgpDecryptor decryptor = PgpDecryptor.create();

	private static byte[] randomBytes(int length) {
		byte[] data = new byte[length];
		new SecureRandom().nextBytes(data);
		return data;
	}

	private static PgpSecretKeys secret(TestPgpKeys.TestKey key) {
		return key.secretKeys(TestPgpKeys.PASSPHRASE);
	}

	private byte[] signAndEncrypt(byte[] data, boolean armored, TestPgpKeys.TestKey[] signers,
			TestPgpKeys.TestKey... recipients) {
		SignRequest.Builder sign = SignRequest.builder();
		for (TestPgpKeys.TestKey signer : signers) {
			sign.signer(secret(signer));
		}
		EncryptRequest.Builder encrypt = EncryptRequest.builder().armored(armored);
		for (TestPgpKeys.TestKey recipient : recipients) {
			encrypt.recipients(recipient.publicKeys());
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.encryptor.signAndEncrypt(sign.build(), encrypt.build(), new ByteArrayInputStream(data), out);
		return out.toByteArray();
	}

	private VerifyResult decryptAndVerify(byte[] message, ByteArrayOutputStream content, TestPgpKeys.TestKey recipient,
			TestPgpKeys.TestKey... signers) {
		VerifyRequest.Builder verify = VerifyRequest.builder();
		for (TestPgpKeys.TestKey signer : signers) {
			verify.keys(signer.publicKeys());
		}
		return this.decryptor.decryptAndVerify(DecryptRequest.builder().keys(secret(recipient)).build(), verify.build(),
				new ByteArrayInputStream(message), content);
	}

	@Test
	void signAndEncryptThenDecryptAndVerifyForRsa() {
		byte[] data = randomBytes(300_000);

		byte[] message = signAndEncrypt(data, false, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA }, TestPgpKeys.RSA);
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = decryptAndVerify(message, content, TestPgpKeys.RSA, TestPgpKeys.RSA);

		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
		assertThat(result.signatures().get(0).signerId())
			.isEqualTo(String.format("%016X", TestPgpKeys.RSA.masterKeyId()));
	}

	@Test
	void signAndEncryptThenDecryptAndVerifyForEc() {
		byte[] data = "hello elliptic curve".getBytes(StandardCharsets.UTF_8);

		byte[] message = signAndEncrypt(data, false, new TestPgpKeys.TestKey[] { TestPgpKeys.EC }, TestPgpKeys.EC);
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		assertThat(decryptAndVerify(message, content, TestPgpKeys.EC, TestPgpKeys.EC).allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void theSignerAndTheRecipientCanBeDifferentKeys() {
		byte[] data = randomBytes(2000);

		byte[] message = signAndEncrypt(data, false, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA }, TestPgpKeys.EC);
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		assertThat(decryptAndVerify(message, content, TestPgpKeys.EC, TestPgpKeys.RSA).allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void severalSignersAndRecipients() {
		byte[] data = randomBytes(2000);

		byte[] message = signAndEncrypt(data, false, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA, TestPgpKeys.EC },
				TestPgpKeys.RSA, TestPgpKeys.EC);

		VerifyResult viaRsa = decryptAndVerify(message, new ByteArrayOutputStream(), TestPgpKeys.RSA, TestPgpKeys.RSA,
				TestPgpKeys.EC);
		VerifyResult viaEc = decryptAndVerify(message, new ByteArrayOutputStream(), TestPgpKeys.EC, TestPgpKeys.RSA,
				TestPgpKeys.EC);
		assertThat(viaRsa.signatures()).hasSize(2).allMatch((s) -> s.status() == SignatureStatus.VALID);
		assertThat(viaEc.signatures()).hasSize(2).allMatch((s) -> s.status() == SignatureStatus.VALID);
	}

	@Test
	void aSignerWhoseKeyWasNotSuppliedIsUnknown() {
		byte[] message = signAndEncrypt(randomBytes(100), false, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA },
				TestPgpKeys.EC);

		VerifyResult result = decryptAndVerify(message, new ByteArrayOutputStream(), TestPgpKeys.EC, TestPgpKeys.EC);

		assertThat(result.signatures()).extracting(SignatureResult::status)
			.containsExactly(SignatureStatus.UNKNOWN_SIGNER);
	}

	@Test
	void armoredOutputIsAsciiArmor() {
		byte[] data = randomBytes(3000);

		byte[] message = signAndEncrypt(data, true, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA }, TestPgpKeys.RSA);
		ByteArrayOutputStream content = new ByteArrayOutputStream();

		assertThat(new String(message, StandardCharsets.US_ASCII)).startsWith("-----BEGIN PGP MESSAGE-----");
		assertThat(decryptAndVerify(message, content, TestPgpKeys.RSA, TestPgpKeys.RSA).allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(data);
	}

	@Test
	void aKeyThatIsNotARecipientIsRejected() {
		byte[] message = signAndEncrypt(randomBytes(100), false, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA },
				TestPgpKeys.RSA);

		assertThatThrownBy(
				() -> decryptAndVerify(message, new ByteArrayOutputStream(), TestPgpKeys.EC, TestPgpKeys.RSA))
			.isInstanceOf(NoMatchingKeyException.class);
	}

	@Test
	void aTamperedMessageIsAnIntegrityFailure() {
		byte[] message = signAndEncrypt(randomBytes(5000), false, new TestPgpKeys.TestKey[] { TestPgpKeys.RSA },
				TestPgpKeys.RSA);
		message[message.length - 1] ^= 0x01;

		assertThatThrownBy(
				() -> decryptAndVerify(message, new ByteArrayOutputStream(), TestPgpKeys.RSA, TestPgpKeys.RSA))
			.isInstanceOf(IntegrityException.class);
	}

	@Test
	void aMessageThatIsEncryptedButNotSignedHasNoValidSignature() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		this.encryptor.encrypt(EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).build(),
				new ByteArrayInputStream(randomBytes(500)), encrypted);

		VerifyResult result = decryptAndVerify(encrypted.toByteArray(), new ByteArrayOutputStream(), TestPgpKeys.RSA,
				TestPgpKeys.RSA);

		assertThat(result.signatures()).isEmpty();
		assertThat(result.allValid()).isFalse();
	}

	@Test
	void aDetachedOrCleartextSignatureCannotBeCombinedWithEncryption() {
		EncryptRequest encrypt = EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).build();
		SignRequest detached = SignRequest.builder()
			.signer(secret(TestPgpKeys.RSA))
			.mode(SignatureMode.DETACHED)
			.build();
		SignRequest cleartext = SignRequest.builder().signer(secret(TestPgpKeys.RSA)).cleartext().build();

		for (SignRequest sign : new SignRequest[] { detached, cleartext }) {
			assertThatThrownBy(() -> this.encryptor.signAndEncrypt(sign, encrypt, new ByteArrayInputStream(new byte[1]),
					new ByteArrayOutputStream()))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void garbageIsACryptoException() {
		assertThatThrownBy(
				() -> decryptAndVerify(randomBytes(200), new ByteArrayOutputStream(), TestPgpKeys.RSA, TestPgpKeys.RSA))
			.isInstanceOf(CryptoException.class);
	}

	@Test
	void theCallersStreamsAreLeftOpen() {
		boolean[] closed = new boolean[2];
		InputStream data = new FilterInputStream(new ByteArrayInputStream(randomBytes(500))) {
			@Override
			public void close() throws IOException {
				closed[0] = true;
				super.close();
			}
		};
		OutputStream out = new FilterOutputStream(new ByteArrayOutputStream()) {
			@Override
			public void close() throws IOException {
				closed[1] = true;
				super.close();
			}
		};

		this.encryptor.signAndEncrypt(SignRequest.builder().signer(secret(TestPgpKeys.RSA)).build(),
				EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).build(), data, out);

		assertThat(closed).containsExactly(false, false);
	}

}
