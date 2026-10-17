package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.PinRequiredException;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgpEncryptDecryptTest {

	/**
	 * Text that is not a message. Random bytes would sometimes happen to parse as a valid
	 * packet.
	 */
	private static final byte[] NOT_A_MESSAGE = "This is only text, and no encrypted or signed message. ".repeat(4)
		.getBytes(StandardCharsets.UTF_8);

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private final PgpEncryptor encryptor = PgpEncryptor.create();

	private final PgpDecryptor decryptor = PgpDecryptor.create();

	private static byte[] randomBytes(int length) {
		byte[] data = new byte[length];
		new SecureRandom().nextBytes(data);
		return data;
	}

	private byte[] encrypt(byte[] data, boolean armored, PgpPublicKeys... recipients) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.encryptor.encrypt(EncryptRequest.builder().recipients(recipients).armored(armored).build(),
				new ByteArrayInputStream(data), out);
		return out.toByteArray();
	}

	private byte[] decrypt(byte[] message, PgpSecretKeys keys, boolean legacy) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.decryptor.decrypt(DecryptRequest.builder().keys(keys).legacyCompatibility(legacy).build(),
				new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	private static PgpSecretKeys secret(TestPgpKeys.TestKey key) {
		return key.secretKeys(TestPgpKeys.PASSPHRASE);
	}

	@Test
	void roundTripsLargeDataForAnRsaRecipient() {
		byte[] data = randomBytes(300_000);

		byte[] message = encrypt(data, false, TestPgpKeys.RSA.publicKeys());

		assertThat(decrypt(message, secret(TestPgpKeys.RSA), false)).isEqualTo(data);
		assertThat(new String(message, StandardCharsets.ISO_8859_1))
			.doesNotContain(new String(data, 0, 32, StandardCharsets.ISO_8859_1));
	}

	@Test
	void roundTripsForAnEcRecipient() {
		byte[] data = "hello elliptic curve".getBytes(StandardCharsets.UTF_8);

		byte[] message = encrypt(data, false, TestPgpKeys.EC.publicKeys());

		assertThat(decrypt(message, secret(TestPgpKeys.EC), false)).isEqualTo(data);
	}

	@Test
	void roundTripsEmptyData() {
		byte[] message = encrypt(new byte[0], false, TestPgpKeys.RSA.publicKeys());

		assertThat(decrypt(message, secret(TestPgpKeys.RSA), false)).isEmpty();
	}

	@Test
	void anyOfSeveralRecipientsCanDecrypt() {
		byte[] data = randomBytes(1000);

		byte[] message = encrypt(data, false, TestPgpKeys.RSA.publicKeys(), TestPgpKeys.EC.publicKeys());

		assertThat(decrypt(message, secret(TestPgpKeys.RSA), false)).isEqualTo(data);
		assertThat(decrypt(message, secret(TestPgpKeys.EC), false)).isEqualTo(data);
	}

	@Test
	void armoredOutputIsAsciiArmorAndDecryptsBack() {
		byte[] data = randomBytes(5000);

		byte[] message = encrypt(data, true, TestPgpKeys.RSA.publicKeys());

		assertThat(new String(message, StandardCharsets.US_ASCII)).startsWith("-----BEGIN PGP MESSAGE-----")
			.contains("-----END PGP MESSAGE-----");
		assertThat(decrypt(message, secret(TestPgpKeys.RSA), false)).isEqualTo(data);
	}

	@Test
	void binaryOutputIsNotArmored() {
		byte[] message = encrypt(randomBytes(10), false, TestPgpKeys.RSA.publicKeys());

		assertThat(message[0] & 0x80).isEqualTo(0x80);
	}

	@Test
	void encryptsToEveryEncryptionSubkeyAndNeverToTheSigningPrimaryKey() throws Exception {
		byte[] message = encrypt(randomBytes(10), false, TestPgpKeys.TWO_SUBKEYS.publicKeys());

		PGPEncryptedDataList list = (PGPEncryptedDataList) new JcaPGPObjectFactory(
				PGPUtil.getDecoderStream(new ByteArrayInputStream(message)))
			.nextObject();
		assertThat(list.size()).isEqualTo(2);
		long[] ids = TestPgpKeys.TWO_SUBKEYS.encryptionKeyIds();
		assertThat(((PGPPublicKeyEncryptedData) list.get(0)).getKeyID()).isIn(ids[0], ids[1]);
		assertThat(((PGPPublicKeyEncryptedData) list.get(1)).getKeyID()).isIn(ids[0], ids[1]);
		assertThat(((PGPPublicKeyEncryptedData) list.get(0)).getKeyID())
			.isNotEqualTo(TestPgpKeys.TWO_SUBKEYS.masterKeyId());
	}

	@Test
	void isIntegrityProtected() throws Exception {
		byte[] message = encrypt(randomBytes(10), false, TestPgpKeys.RSA.publicKeys());

		PGPEncryptedDataList list = (PGPEncryptedDataList) new JcaPGPObjectFactory(
				PGPUtil.getDecoderStream(new ByteArrayInputStream(message)))
			.nextObject();
		assertThat(((PGPPublicKeyEncryptedData) list.get(0)).isIntegrityProtected()).isTrue();
	}

	@Test
	void aKeyThatIsNotARecipientIsRejected() {
		byte[] message = encrypt(randomBytes(100), false, TestPgpKeys.RSA.publicKeys());

		assertThatThrownBy(() -> decrypt(message, secret(TestPgpKeys.EC), false))
			.isInstanceOf(NoMatchingKeyException.class);
	}

	@Test
	void aWrongPassphraseIsReportedAsSuch() {
		byte[] message = encrypt(randomBytes(100), false, TestPgpKeys.RSA.publicKeys());
		PgpSecretKeys wrong = TestPgpKeys.RSA.secretKeys("not-the-passphrase".toCharArray());

		assertThatThrownBy(() -> decrypt(message, wrong, false)).isInstanceOf(PinRequiredException.class)
			.hasMessageNotContaining("not-the-passphrase");
	}

	@Test
	void aTamperedMessageIsAnIntegrityFailure() {
		byte[] data = randomBytes(5000);
		byte[] lastByte = encrypt(data, false, TestPgpKeys.RSA.publicKeys());
		lastByte[lastByte.length - 1] ^= 0x01;
		byte[] middle = encrypt(data, false, TestPgpKeys.RSA.publicKeys());
		middle[middle.length - 2500] ^= 0x01;

		assertThatThrownBy(() -> decrypt(lastByte, secret(TestPgpKeys.RSA), false))
			.isInstanceOf(IntegrityException.class);
		assertThatThrownBy(() -> decrypt(middle, secret(TestPgpKeys.RSA), false))
			.isInstanceOf(IntegrityException.class);
	}

	@Test
	void garbageIsACryptoException() {
		assertThatThrownBy(() -> decrypt(NOT_A_MESSAGE, secret(TestPgpKeys.RSA), false))
			.isInstanceOf(CryptoException.class);
		assertThatThrownBy(() -> decrypt(new byte[0], secret(TestPgpKeys.RSA), false))
			.isInstanceOf(CryptoException.class);
	}

	private static byte[] legacyMessage(byte[] data, int algorithm, boolean integrityPacket) throws Exception {
		PGPPublicKeyRing ring = new JcaPGPPublicKeyRingCollection(PGPUtil.getDecoderStream(
				new ByteArrayInputStream(TestPgpKeys.RSA.publicArmored().getBytes(StandardCharsets.US_ASCII))))
			.getKeyRings()
			.next();
		PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(
				new JcePGPDataEncryptorBuilder(algorithm).setWithIntegrityPacket(integrityPacket)
					.setSecureRandom(new SecureRandom())
					.setProvider(BC));
		generator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(
				ring.getPublicKeys().next() == null ? null : encryptionKey(ring))
			.setProvider(BC));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OutputStream encrypted = generator.open(out, new byte[4096])) {
			try (OutputStream literal = new PGPLiteralDataGenerator().open(encrypted, PGPLiteralData.BINARY, "x",
					data.length, new Date())) {
				literal.write(data);
			}
		}
		return out.toByteArray();
	}

	private static org.bouncycastle.openpgp.PGPPublicKey encryptionKey(PGPPublicKeyRing ring) {
		return ring.getPublicKey(TestPgpKeys.RSA.encryptionKeyIds()[0]);
	}

	@Test
	void messagesWithoutIntegrityProtectionAreRefusedUnlessLegacyCompatibilityIsSet() throws Exception {
		byte[] data = randomBytes(300);
		byte[] message = legacyMessage(data, SymmetricKeyAlgorithmTags.AES_256, false);

		assertThatThrownBy(() -> decrypt(message, secret(TestPgpKeys.RSA), false)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("legacy");
		assertThat(decrypt(message, secret(TestPgpKeys.RSA), true)).isEqualTo(data);
	}

	@Test
	void weakCiphersAreRefusedUnlessLegacyCompatibilityIsSet() throws Exception {
		byte[] data = randomBytes(300);
		byte[] message = legacyMessage(data, SymmetricKeyAlgorithmTags.TRIPLE_DES, true);

		assertThatThrownBy(() -> decrypt(message, secret(TestPgpKeys.RSA), false)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("legacy");
		assertThat(decrypt(message, secret(TestPgpKeys.RSA), true)).isEqualTo(data);
	}

	@Test
	void aRequestNeedsAtLeastOneRecipient() {
		assertThatThrownBy(() -> EncryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aDecryptRequestNeedsKeys() {
		assertThatThrownBy(() -> DecryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unreadableKeysAreACryptoException() {
		assertThatThrownBy(() -> PgpPublicKeys.fromArmored("not a key")).isInstanceOf(CryptoException.class);
		assertThatThrownBy(() -> PgpSecretKeys.fromArmored("not a key", TestPgpKeys.PASSPHRASE))
			.isInstanceOf(CryptoException.class);
	}

}
