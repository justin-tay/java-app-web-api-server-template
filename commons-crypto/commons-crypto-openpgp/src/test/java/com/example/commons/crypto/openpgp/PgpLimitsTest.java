package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

import com.example.commons.crypto.CryptoException;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgpLimitsTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static void writeLiteral(OutputStream out, byte[] data) throws IOException {
		try (OutputStream literal = new PGPLiteralDataGenerator().open(out, PGPLiteralData.BINARY, "x", data.length,
				new Date())) {
			literal.write(data);
		}
	}

	private static void writeCompressed(OutputStream out, int depth, byte[] data) throws IOException {
		if (depth == 0) {
			writeLiteral(out, data);
			return;
		}
		PGPCompressedDataGenerator generator = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZLIB);
		try (OutputStream compressed = generator.open(out)) {
			writeCompressed(compressed, depth - 1, data);
		}
	}

	private static byte[] compressed(int depth, byte[] data) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeCompressed(out, depth, data);
		return out.toByteArray();
	}

	/**
	 * Encrypts the given packets for the RSA test key, as an application that compresses
	 * would.
	 */
	private static byte[] encrypted(byte[] packets) throws Exception {
		PGPPublicKey key = new JcaPGPPublicKeyRingCollection(PGPUtil.getDecoderStream(
				new ByteArrayInputStream(TestPgpKeys.RSA.publicArmored().getBytes(StandardCharsets.US_ASCII))))
			.getKeyRings()
			.next()
			.getPublicKey(TestPgpKeys.RSA.encryptionKeyIds()[0]);
		PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(
				new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true)
					.setSecureRandom(new SecureRandom())
					.setProvider(BC));
		generator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(BC));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OutputStream encrypted = generator.open(out, new byte[4096])) {
			encrypted.write(packets);
		}
		return out.toByteArray();
	}

	private static void decrypt(byte[] message, long limit, OutputStream out) {
		PgpDecryptor.create()
			.decrypt(DecryptRequest.builder()
				.keys(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE))
				.maxOutputBytes(limit)
				.build(), new ByteArrayInputStream(message), out);
	}

	@Test
	void aMessageNestedInTooManyCompressionLayersIsRefused() throws Exception {
		byte[] message = encrypted(compressed(20, "deep".getBytes(StandardCharsets.UTF_8)));

		assertThatThrownBy(() -> decrypt(message, Long.MAX_VALUE, new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("nested");
	}

	@Test
	void aSignedMessageNestedInTooManyCompressionLayersIsRefused() throws Exception {
		byte[] message = compressed(20, "deep".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> PgpVerifier.create()
			.verify(VerifyRequest.builder().keys(TestPgpKeys.RSA.publicKeys()).build(),
					new ByteArrayInputStream(message), new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("nested");
	}

	@Test
	void aFewCompressionLayersAreFine() throws Exception {
		byte[] data = "shallow".getBytes(StandardCharsets.UTF_8);
		byte[] message = encrypted(compressed(3, data));
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		decrypt(message, Long.MAX_VALUE, out);

		assertThat(out.toByteArray()).isEqualTo(data);
	}

	@Test
	void theOutputLimitStopsADecompressionBomb() throws Exception {
		byte[] message = encrypted(compressed(1, new byte[8_000_000]));
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		assertThatThrownBy(() -> decrypt(message, 100_000, out)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("limit");
		assertThat(out.size()).isLessThanOrEqualTo(100_000);
	}

	@Test
	void theOutputLimitAppliesToAPlainMessageToo() {
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.encrypt(EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).build(),
					new ByteArrayInputStream(new byte[10_000]), encrypted);

		assertThatThrownBy(() -> decrypt(encrypted.toByteArray(), 1000, new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("limit");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		decrypt(encrypted.toByteArray(), 10_000, out);
		assertThat(out.size()).isEqualTo(10_000);
	}

	@Test
	void theOutputLimitAppliesToVerifyingAnEmbeddedSignature() {
		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		PgpSigner.create()
			.sign(SignRequest.builder().signer(TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE)).build(),
					new ByteArrayInputStream(new byte[10_000]), signed);
		VerifyRequest request = VerifyRequest.builder().keys(TestPgpKeys.RSA.publicKeys()).maxOutputBytes(1000).build();

		assertThatThrownBy(() -> PgpVerifier.create()
			.verify(request, new ByteArrayInputStream(signed.toByteArray()), new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("limit");
	}

	@Test
	void theLimitMustBePositive() {
		assertThatThrownBy(() -> DecryptRequest.builder().maxOutputBytes(0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VerifyRequest.builder().maxOutputBytes(-5))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
