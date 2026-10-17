package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.PinRequiredException;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Messages that name their recipient differently: to nobody in particular (a wildcard key
 * ID, used to hide who the recipient is), and to a passphrase.
 */
class PgpRecipientFormsTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "A short message".getBytes(StandardCharsets.UTF_8);

	private static final char[] PASSPHRASE = "correct horse battery staple".toCharArray();

	/** A message to the key whose ID is hidden behind the wildcard ID 0. */
	private static byte[] wildcardMessage(TestPgpKeys.TestKey recipient) throws Exception {
		PGPPublicKey key = recipient.publicKeys().encryptionKeys().get(0);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(
				new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true)
					.setProvider(BC));
		generator
			.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(BC).setUseWildcardKeyID(true));
		try (java.io.OutputStream encrypted = generator.open(out, new byte[4096]);
				java.io.OutputStream literal = new PGPLiteralDataGenerator().open(encrypted, PGPLiteralData.BINARY, "",
						new Date(), new byte[4096])) {
			literal.write(DATA);
		}
		return out.toByteArray();
	}

	private static byte[] decrypt(byte[] message, DecryptRequest.Builder request) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpDecryptor.create().decrypt(request.build(), new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	private static byte[] encryptWithPassphrase(char[] passphrase) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.encrypt(EncryptRequest.builder().passphrase(passphrase).build(), new ByteArrayInputStream(DATA), out);
		return out.toByteArray();
	}

	@Test
	void aWildcardMessageIsDecryptedByTryingTheKeys() throws Exception {
		byte[] message = wildcardMessage(TestPgpKeys.EC);
		// The right key is not the first one.
		PgpSecretKeys keys = PgpSecretKeys.fromArmored(
				TestPgpKeys.RSA.secretArmored() + "\n" + TestPgpKeys.EC.secretArmored(), TestPgpKeys.PASSPHRASE);

		assertThat(decrypt(message, DecryptRequest.builder().keys(keys))).isEqualTo(DATA);
	}

	@Test
	void aWildcardMessageForAnotherKeyHasNoMatchingKey() throws Exception {
		byte[] message = wildcardMessage(TestPgpKeys.EC);

		assertThatThrownBy(() -> decrypt(message,
				DecryptRequest.builder().keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE))))
			.isInstanceOf(NoMatchingKeyException.class);
	}

	@Test
	void aPassphraseMessageRoundTrips() {
		byte[] message = encryptWithPassphrase(PASSPHRASE);

		assertThat(decrypt(message, DecryptRequest.builder().passphrase(PASSPHRASE))).isEqualTo(DATA);
	}

	@Test
	void aWrongPassphraseIsReportedAsOne() {
		byte[] message = encryptWithPassphrase(PASSPHRASE);

		assertThatThrownBy(() -> decrypt(message, DecryptRequest.builder().passphrase("not it".toCharArray())))
			.isInstanceOf(PinRequiredException.class);
	}

	@Test
	void aPassphraseMessageHasNoMatchingKeyForSecretKeysAlone() {
		byte[] message = encryptWithPassphrase(PASSPHRASE);

		assertThatThrownBy(() -> decrypt(message,
				DecryptRequest.builder().keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE))))
			.isInstanceOf(NoMatchingKeyException.class);
	}

	@Test
	void aMessageForKeysAndAPassphraseOpensWithEither() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.encrypt(EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).passphrase(PASSPHRASE).build(),
					new ByteArrayInputStream(DATA), out);
		byte[] message = out.toByteArray();

		assertThat(decrypt(message, DecryptRequest.builder().passphrase(PASSPHRASE))).isEqualTo(DATA);
		assertThat(decrypt(message, DecryptRequest.builder().keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE))))
			.isEqualTo(DATA);
	}

	@Test
	void aPassphraseMessageWithoutAnIntegrityCheckIsRefused() {
		// Altered data in a passphrase message is caught like any other.
		byte[] message = encryptWithPassphrase(PASSPHRASE);
		message[message.length - 10] ^= 1;

		assertThatThrownBy(() -> decrypt(message, DecryptRequest.builder().passphrase(PASSPHRASE)))
			.isInstanceOf(CryptoException.class);
	}

	@Test
	void anEncryptRequestNeedsARecipientOrAPassphrase() {
		assertThatThrownBy(() -> EncryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aDecryptRequestNeedsKeysOrAPassphrase() {
		assertThatThrownBy(() -> DecryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
