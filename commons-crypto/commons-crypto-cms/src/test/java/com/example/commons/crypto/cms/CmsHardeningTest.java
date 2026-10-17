package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.TokenUnavailableException;
import com.example.commons.crypto.testsupport.FailingProvider;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputAEADEncryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The output limit, the way failures are classified, what the messages contain, and what
 * is written, for CMS.
 */
class CmsHardeningTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "The quick brown fox jumps over the lazy dog. ".repeat(300)
		.getBytes(StandardCharsets.UTF_8);

	private static PrivateKeyHandle handle(PrivateKey key, Provider provider) {
		return new PrivateKeyHandle() {
			@Override
			public PrivateKey key() {
				return key;
			}

			@Override
			public Optional<Provider> provider() {
				return Optional.of(provider);
			}

			@Override
			public void close() {
			}
		};
	}

	private static byte[] encrypted(byte[] data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Cms.encrypt(new ByteArrayInputStream(data), out, TestKeys.certificate("rsa"));
		return out.toByteArray();
	}

	private static byte[] signedAndEncrypted(byte[] data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CmsEncryptor.create()
				.signAndEncrypt(SignRequest.builder().signer(key, TestKeys.certificate("rsa")).build(),
						EncryptRequest.builder()
							.recipients(CmsRecipient.certificate(TestKeys.certificate("rsa")))
							.build(),
						new ByteArrayInputStream(data), out);
		}
		return out.toByteArray();
	}

	// ---- the output limit

	@Test
	void theOutputLimitStopsDecrypting() throws Exception {
		byte[] message = encrypted(DATA);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, TestKeys.certificate("rsa")).maxOutputBytes(1000).build(),
						new ByteArrayInputStream(message), out))
				.isInstanceOf(CryptoException.class)
				.hasMessageContaining("limit");
		}
		assertThat(out.size()).isLessThanOrEqualTo(1000);
	}

	@Test
	void theOutputLimitStopsDecryptingAndVerifying() throws Exception {
		byte[] message = signedAndEncrypted(DATA);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> CmsDecryptor.create()
				.decryptAndVerify(
						DecryptRequest.builder().key(key, TestKeys.certificate("rsa")).maxOutputBytes(1000).build(),
						VerifyRequest.builder().certificates(TestKeys.certificate("rsa")).build(),
						new ByteArrayInputStream(message), out))
				.isInstanceOf(CryptoException.class)
				.hasMessageContaining("limit");
		}
		assertThat(out.size()).isLessThanOrEqualTo(1000);
	}

	@Test
	void theOutputLimitStopsVerifyingAnEmbeddedSignature() throws Exception {
		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			Cms.sign(new ByteArrayInputStream(DATA), signed, key, TestKeys.certificate("rsa"));
		}
		VerifyRequest request = VerifyRequest.builder()
			.certificates(TestKeys.certificate("rsa"))
			.maxOutputBytes(1000)
			.build();

		assertThatThrownBy(() -> CmsVerifier.create()
			.verify(request, new ByteArrayInputStream(signed.toByteArray()), new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("limit");
	}

	@Test
	void aLimitThatIsBigEnoughChangesNothing() throws Exception {
		byte[] message = encrypted(DATA);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CmsDecryptor.create()
				.decrypt(DecryptRequest.builder()
					.key(key, TestKeys.certificate("rsa"))
					.maxOutputBytes(DATA.length)
					.build(), new ByteArrayInputStream(message), out);
		}

		assertThat(out.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void theLimitMustBePositive() {
		assertThatThrownBy(() -> DecryptRequest.builder().maxOutputBytes(0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VerifyRequest.builder().maxOutputBytes(-1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	// ---- classification

	@Test
	void aFailureOfTheCallersStreamIsNotACorruptMessage() throws Exception {
		byte[] message = encrypted(DATA);
		OutputStream broken = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new IOException("disk full");
			}
		};

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			assertThatThrownBy(() -> CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, TestKeys.certificate("rsa")).build(),
						new ByteArrayInputStream(message), broken))
				.isExactlyInstanceOf(CryptoException.class)
				.hasMessage("Cannot write to the output stream");
		}
	}

	@Test
	void onlyARealIntegrityFailureIsAnIntegrityFailure() {
		assertThat(CmsDecryptor.failure(new CMSException("mac check in GCM failed")))
			.isInstanceOf(IntegrityException.class);
		assertThat(CmsDecryptor.failure(new CMSException("exception unwrapping key: a macro failed")))
			.isNotInstanceOf(IntegrityException.class);
		assertThat(CmsDecryptor.failure(new CMSException("the machine is busy")))
			.isNotInstanceOf(IntegrityException.class);
	}

	// ---- a token that goes away during an operation

	@Test
	void signingWithATokenThatIsRemovedIsATokenUnavailableException() throws Exception {
		try (PrivateKeyHandle software = TestKeys.handle("rsa")) {
			PrivateKeyHandle key = handle(software.key(), FailingProvider.failingSignature(BC, "SHA256withRSA"));

			assertThatThrownBy(() -> Cms.sign(new ByteArrayInputStream(DATA), new ByteArrayOutputStream(), key,
					TestKeys.certificate("rsa")))
				.isInstanceOf(TokenUnavailableException.class);
		}
	}

	@Test
	void decryptingWithATokenThatIsRemovedIsATokenUnavailableException() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		CMSAuthEnvelopedDataGenerator generator = new CMSAuthEnvelopedDataGenerator();
		generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(certificate).setProvider(BC));
		byte[] message = generator
			.generate(new CMSProcessableByteArray(DATA),
					(OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM).setProvider(BC)
						.build())
			.getEncoded();

		try (PrivateKeyHandle software = TestKeys.handle("rsa")) {
			PrivateKeyHandle key = handle(software.key(), FailingProvider.failingCipher(BC, "RSA"));

			assertThatThrownBy(() -> CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, certificate).legacyCompatibility(true).build(),
						new ByteArrayInputStream(message), new ByteArrayOutputStream()))
				.isInstanceOf(TokenUnavailableException.class);
		}
	}

	// ---- what the messages contain

	@Test
	void failureMessagesNeverContainTheContent() throws Exception {
		String secret = "TOP-SECRET-CONTENT-" + "x".repeat(40);
		byte[] message = encrypted(secret.getBytes(StandardCharsets.UTF_8));
		message[message.length - 10] ^= 0x01;

		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CryptoException failure = null;
			try {
				CmsDecryptor.create()
					.decrypt(DecryptRequest.builder().key(key, TestKeys.certificate("rsa")).build(),
							new ByteArrayInputStream(message), new ByteArrayOutputStream());
			}
			catch (CryptoException ex) {
				failure = ex;
			}
			assertThat(failure).isNotNull();
			List<String> messages = new ArrayList<>();
			for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
				messages.add(String.valueOf(cause.getMessage()));
			}
			assertThat(messages).noneMatch((text) -> text.contains("TOP-SECRET"));
		}
	}

	// ---- what is written

	@Test
	void signaturesAreMadeWithSha256() throws Exception {
		for (String name : new String[] { "rsa", "ec" }) {
			ByteArrayOutputStream signed = new ByteArrayOutputStream();
			try (PrivateKeyHandle key = TestKeys.handle(name)) {
				Cms.sign(new ByteArrayInputStream(DATA), signed, key, TestKeys.certificate(name));
			}

			CMSSignedData parsed = new CMSSignedData(signed.toByteArray());
			for (SignerInformation signer : parsed.getSignerInfos()) {
				assertThat(signer.getDigestAlgOID()).as(name).isEqualTo(NISTObjectIdentifiers.id_sha256.getId());
			}
		}
	}

}
