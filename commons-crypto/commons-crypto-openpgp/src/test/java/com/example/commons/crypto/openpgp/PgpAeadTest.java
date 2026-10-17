package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.bouncycastle.bcpg.AEADAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Messages that use authenticated encryption in the packets (OCB, in the styles of
 * LibrePGP and of RFC 9580), made with Bouncy Castle.
 */
class PgpAeadTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "A message in AEAD packets. ".repeat(3000).getBytes(StandardCharsets.UTF_8);

	private static byte[] message(JcePGPDataEncryptorBuilder builder, PGPPublicKey key, char[] passphrase)
			throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(builder.setProvider(BC));
		if (key != null) {
			generator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(BC));
		}
		if (passphrase != null) {
			generator.addMethod(new JcePBEKeyEncryptionMethodGenerator(passphrase,
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC)
						.build()
						.get(org.bouncycastle.bcpg.HashAlgorithmTags.SHA256),
					0x60)
				.setProvider(BC));
		}
		try (OutputStream encrypted = generator.open(out, new byte[1 << 16]);
				OutputStream literal = new PGPLiteralDataGenerator().open(encrypted, PGPLiteralData.BINARY, "",
						new Date(), new byte[1 << 16])) {
			literal.write(DATA);
		}
		return out.toByteArray();
	}

	private static JcePGPDataEncryptorBuilder aead(boolean v6) {
		JcePGPDataEncryptorBuilder builder = new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
			.setWithAEAD(AEADAlgorithmTags.OCB, 6);
		return v6 ? builder.setUseV6AEAD() : builder.setUseV5AEAD();
	}

	private static byte[] decrypt(byte[] message, DecryptRequest.Builder request) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpDecryptor.create().decrypt(request.build(), new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void anAeadMessageToAKeyIsDecrypted(boolean v6) throws Exception {
		byte[] message = message(aead(v6), TestPgpKeys.RSA.publicKeys().encryptionKeys().get(0), null);

		assertThat(decrypt(message, DecryptRequest.builder().keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE))))
			.isEqualTo(DATA);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void anAeadMessageToAPassphraseIsDecrypted(boolean v6) throws Exception {
		char[] passphrase = "correct horse".toCharArray();
		byte[] message = message(aead(v6), null, passphrase);

		assertThat(decrypt(message, DecryptRequest.builder().passphrase(passphrase))).isEqualTo(DATA);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void analteredAeadMessageIsAnError(boolean v6) throws Exception {
		byte[] message = message(aead(v6), TestPgpKeys.EC.publicKeys().encryptionKeys().get(0), null);
		message[message.length / 2] ^= 1;

		assertThatThrownBy(() -> decrypt(message,
				DecryptRequest.builder().keys(TestPgpKeys.EC.secretKeys(TestPgpKeys.PASSPHRASE))))
			.isInstanceOf(com.example.commons.crypto.CryptoException.class);
	}

	@Test
	void anAeadMessageThatIsCutShortIsAnError() throws Exception {
		byte[] message = message(aead(false), TestPgpKeys.RSA.publicKeys().encryptionKeys().get(0), null);
		byte[] cut = java.util.Arrays.copyOf(message, message.length - 40);

		assertThatThrownBy(
				() -> decrypt(cut, DecryptRequest.builder().keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE))))
			.isInstanceOf(com.example.commons.crypto.CryptoException.class);
	}

}
