package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.PrivateKeyHandle;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsEncryptDecryptTest {

	/**
	 * Text that is not a message. Random bytes would sometimes happen to parse as a valid
	 * packet.
	 */
	private static final byte[] NOT_A_MESSAGE = "This is only text, and no encrypted or signed message. ".repeat(4)
		.getBytes(StandardCharsets.UTF_8);

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private final CmsEncryptor encryptor = CmsEncryptor.create();

	private final CmsDecryptor decryptor = CmsDecryptor.create();

	private static byte[] randomBytes(int length) {
		byte[] data = new byte[length];
		new SecureRandom().nextBytes(data);
		return data;
	}

	private byte[] encrypt(byte[] data, boolean armored, X509Certificate... certificates) {
		EncryptRequest.Builder request = EncryptRequest.builder().armored(armored);
		for (X509Certificate certificate : certificates) {
			request.recipients(CmsRecipient.certificate(certificate));
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.encryptor.encrypt(request.build(), new ByteArrayInputStream(data), out);
		return out.toByteArray();
	}

	private byte[] decrypt(byte[] message, PrivateKeyHandle key, X509Certificate certificate, boolean legacy) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.decryptor.decrypt(DecryptRequest.builder().key(key, certificate).legacyCompatibility(legacy).build(),
				new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	@Test
	void roundTripsLargeDataForAnRsaRecipient() throws Exception {
		byte[] data = randomBytes(300_000);
		X509Certificate certificate = TestKeys.certificate("rsa");

		byte[] message = encrypt(data, false, certificate);

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThat(decrypt(message, key, certificate, false)).isEqualTo(data);
		}
		assertThat(new String(message, StandardCharsets.ISO_8859_1))
			.doesNotContain(new String(Arrays.copyOf(data, 32), StandardCharsets.ISO_8859_1));
	}

	@Test
	void roundTripsForAnEcRecipient() throws Exception {
		byte[] data = "hello elliptic curve".getBytes(StandardCharsets.UTF_8);
		X509Certificate certificate = TestKeys.certificate("ec");

		byte[] message = encrypt(data, false, certificate);

		try (PrivateKeyHandle key = TestKeys.handle("ec")) {
			assertThat(decrypt(message, key, certificate, false)).isEqualTo(data);
		}
	}

	@Test
	void roundTripsEmptyData() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");

		byte[] message = encrypt(new byte[0], false, certificate);

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThat(decrypt(message, key, certificate, false)).isEmpty();
		}
	}

	@Test
	void anyOfSeveralRecipientsCanDecrypt() throws Exception {
		byte[] data = randomBytes(1000);
		X509Certificate rsa = TestKeys.certificate("rsa");
		X509Certificate ec = TestKeys.certificate("ec");

		byte[] message = encrypt(data, false, rsa, ec);

		try (PrivateKeyHandle rsaKey = TestKeys.handle("rsa"); PrivateKeyHandle ecKey = TestKeys.handle("ec")) {
			assertThat(decrypt(message, rsaKey, rsa, false)).isEqualTo(data);
			assertThat(decrypt(message, ecKey, ec, false)).isEqualTo(data);
		}
	}

	@Test
	void armoredOutputIsPemAndDecryptsBack() throws Exception {
		byte[] data = randomBytes(5000);
		X509Certificate certificate = TestKeys.certificate("rsa");

		byte[] message = encrypt(data, true, certificate);

		String text = new String(message, StandardCharsets.US_ASCII);
		assertThat(text).startsWith("-----BEGIN CMS-----\n").endsWith("-----END CMS-----\n");
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThat(decrypt(message, key, certificate, false)).isEqualTo(data);
		}
	}

	@Test
	void binaryOutputIsDer() throws Exception {
		byte[] message = encrypt(randomBytes(10), false, TestKeys.certificate("rsa"));

		assertThat(message[0]).isEqualTo((byte) 0x30);
	}

	@Test
	void usesOaepAndAuthenticatedEncryptionByDefault() throws Exception {
		byte[] message = encrypt(randomBytes(10), false, TestKeys.certificate("rsa"));

		CMSAuthEnvelopedData parsed = new CMSAuthEnvelopedData(message);
		RecipientInformation recipient = parsed.getRecipientInfos().getRecipients().iterator().next();
		assertThat(recipient.getKeyEncryptionAlgorithm().getAlgorithm()).isEqualTo(PKCSObjectIdentifiers.id_RSAES_OAEP);
		assertThat(parsed.getEncryptionAlgOID()).isEqualTo(NISTObjectIdentifiers.id_aes256_GCM.getId());
	}

	@Test
	void aKeyThatIsNotARecipientIsRejected() throws Exception {
		X509Certificate rsa = TestKeys.certificate("rsa");
		byte[] message = encrypt(randomBytes(100), false, rsa);

		try (PrivateKeyHandle ecKey = TestKeys.handle("ec")) {
			assertThatThrownBy(() -> decrypt(message, ecKey, TestKeys.certificate("ec"), false))
				.isInstanceOf(NoMatchingKeyException.class);
		}
	}

	@Test
	void aTamperedAuthenticationTagIsAnIntegrityFailure() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		byte[] message = encrypt(randomBytes(2000), false, certificate);
		// The message ends with the 16 byte tag followed by three pairs of
		// end-of-contents octets.
		message[message.length - 10] ^= 0x01;

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> decrypt(message, key, certificate, false)).isInstanceOf(IntegrityException.class);
		}
	}

	@Test
	void tamperedContentIsAnIntegrityFailure() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		byte[] message = encrypt(randomBytes(2000), false, certificate);
		message[message.length - 600] ^= 0x01;

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> decrypt(message, key, certificate, false)).isInstanceOf(IntegrityException.class);
		}
	}

	@Test
	void garbageIsACryptoException() throws Exception {
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> decrypt(NOT_A_MESSAGE, key, TestKeys.certificate("rsa"), false))
				.isInstanceOf(CryptoException.class);
		}
	}

	@Test
	void unauthenticatedEnvelopedDataIsRefusedUnlessLegacyCompatibilityIsSet() throws Exception {
		byte[] data = randomBytes(500);
		X509Certificate certificate = TestKeys.certificate("rsa");
		CMSEnvelopedDataGenerator generator = new CMSEnvelopedDataGenerator();
		generator.addRecipientInfoGenerator(
				new JceKeyTransRecipientInfoGenerator(certificate, new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
						PKCSObjectIdentifiers.id_RSAES_OAEP, new org.bouncycastle.asn1.pkcs.RSAESOAEPparams()))
					.setProvider(BC));
		byte[] message = generator
			.generate(new CMSProcessableByteArray(data),
					new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).setProvider(BC).build())
			.getEncoded();

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> decrypt(message, key, certificate, false)).isInstanceOf(CryptoException.class)
				.hasMessageContaining("legacy");
			assertThat(decrypt(message, key, certificate, true)).isEqualTo(data);
		}
	}

	@Test
	void pkcs1v15KeyTransportIsRefusedUnlessLegacyCompatibilityIsSet() throws Exception {
		byte[] data = randomBytes(500);
		X509Certificate certificate = TestKeys.certificate("rsa");
		org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator generator = new org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator();
		generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(certificate).setProvider(BC));
		byte[] message = generator
			.generate(new CMSProcessableByteArray(data),
					(org.bouncycastle.operator.OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(
							CMSAlgorithm.AES256_GCM)
						.setProvider(BC)
						.build())
			.getEncoded();

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> decrypt(message, key, certificate, false)).isInstanceOf(CryptoException.class)
				.hasMessageContaining("PKCS#1 v1.5");
			assertThat(decrypt(message, key, certificate, true)).isEqualTo(data);
		}
	}

	@Test
	void aRequestNeedsAtLeastOneRecipient() {
		assertThatThrownBy(() -> EncryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aDecryptRequestNeedsAKey() {
		assertThatThrownBy(() -> DecryptRequest.builder().build()).isInstanceOf(IllegalStateException.class);
	}

}
