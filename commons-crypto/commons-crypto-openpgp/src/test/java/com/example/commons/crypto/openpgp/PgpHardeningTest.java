package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.TokenUnavailableException;
import com.example.commons.crypto.testsupport.FailingProvider;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Key validity when encrypting, the way failures are classified, what the messages
 * contain, and what is written, for OpenPGP.
 */
class PgpHardeningTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "The quick brown fox jumps over the lazy dog. ".repeat(300)
		.getBytes(StandardCharsets.UTF_8);

	private static PrivateKeyHandle handle(KeyPair keyPair, Provider provider) {
		return new PrivateKeyHandle() {
			@Override
			public java.security.PrivateKey key() {
				return keyPair.getPrivate();
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

	private static byte[] encrypt(PgpPublicKeys recipient) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), out, recipient);
		return out.toByteArray();
	}

	private static PGPEncryptedDataList list(byte[] message) throws Exception {
		return (PGPEncryptedDataList) new JcaPGPObjectFactory(
				PGPUtil.getDecoderStream(new ByteArrayInputStream(message)))
			.nextObject();
	}

	// ---- which keys may encrypt

	@Test
	void aRingWhosePrimaryKeyHasExpiredIsNotUsed() {
		PgpPublicKeys expired = TestPgpKeys.EXPIRED_PRIMARY.publicKeys();

		assertThatThrownBy(() -> encrypt(expired)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("no usable encryption key");
	}

	@Test
	void aRingWhosePrimaryKeyIsRevokedIsNotUsed() {
		PgpPublicKeys revoked = TestPgpKeys.REVOKED_PRIMARY.publicKeys();

		assertThatThrownBy(() -> encrypt(revoked)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("no usable encryption key");
	}

	@Test
	void anExpiredSubkeyIsSkippedAndTheOtherIsUsed() throws Exception {
		byte[] message = encrypt(TestPgpKeys.EXPIRED_FIRST_SUBKEY.publicKeys());

		PGPEncryptedDataList list = list(message);
		assertThat(list.size()).isEqualTo(1);
		assertThat(((PGPPublicKeyEncryptedData) list.get(0)).getKeyID())
			.isEqualTo(TestPgpKeys.EXPIRED_FIRST_SUBKEY.encryptionKeyIds()[1]);
	}

	// ---- classification

	@Test
	void aFailureOfTheCallersStreamIsNotACorruptMessage() {
		byte[] message = encrypt(TestPgpKeys.RSA.publicKeys());
		OutputStream broken = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new IOException("disk full");
			}
		};

		assertThatThrownBy(() -> Pgp.decrypt(new ByteArrayInputStream(message), broken,
				TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE)))
			.isExactlyInstanceOf(CryptoException.class)
			.hasMessage("Cannot write to the output stream");
	}

	// ---- a token that goes away during an operation

	@Test
	void signingWithATokenThatIsRemovedIsATokenUnavailableException() throws Exception {
		KeyPair keyPair = TokenKeyRings.softwareKeyPair(false);
		PgpPublicKeys publicKeys = TokenKeyRings.ring(keyPair.getPublic(), KeyFlags.SIGN_DATA);
		// The token works while the keys are paired, and is gone when it is time to sign.
		AtomicReference<Provider> provider = new AtomicReference<>(BC);
		PrivateKeyHandle handle = new PrivateKeyHandle() {
			@Override
			public java.security.PrivateKey key() {
				return keyPair.getPrivate();
			}

			@Override
			public Optional<Provider> provider() {
				return Optional.of(provider.get());
			}

			@Override
			public void close() {
			}
		};
		PgpSecretKeys keys = PgpSecretKeys.token(handle, publicKeys);
		provider.set(FailingProvider.failingSignature(BC, ""));

		assertThatThrownBy(() -> Pgp.sign(new ByteArrayInputStream(DATA), new ByteArrayOutputStream(), keys))
			.isInstanceOf(TokenUnavailableException.class);
	}

	@Test
	void decryptingWithATokenThatIsRemovedIsATokenUnavailableException() throws Exception {
		KeyPair keyPair = TokenKeyRings.softwareKeyPair(false);
		PgpPublicKeys publicKeys = TokenKeyRings.ring(keyPair.getPublic(), TokenKeyRings.ENCRYPT);
		byte[] message = encrypt(publicKeys);
		PgpSecretKeys keys = PgpSecretKeys.token(handle(keyPair, FailingProvider.failingCipher(BC, "RSA")), publicKeys);

		assertThatThrownBy(() -> Pgp.decrypt(new ByteArrayInputStream(message), new ByteArrayOutputStream(), keys))
			.isInstanceOf(TokenUnavailableException.class);
	}

	@Test
	void pairingATokenKeyThatIsAlreadyGoneIsATokenUnavailableException() throws Exception {
		KeyPair keyPair = TokenKeyRings.softwareKeyPair(false);
		PgpPublicKeys publicKeys = TokenKeyRings.ring(keyPair.getPublic(), KeyFlags.SIGN_DATA);
		PrivateKeyHandle gone = handle(keyPair, FailingProvider.failingSignature(BC, "SHA256withRSA"));

		assertThatThrownBy(() -> PgpSecretKeys.token(gone, publicKeys)).isInstanceOf(TokenUnavailableException.class);
	}

	// ---- what the messages contain

	@Test
	void failureMessagesNeverContainTheContent() {
		String secret = "TOP-SECRET-CONTENT-" + "x".repeat(40);
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(secret.getBytes(StandardCharsets.UTF_8)), encrypted,
				TestPgpKeys.RSA.publicKeys());
		byte[] message = encrypted.toByteArray();
		message[message.length - 1] ^= 0x01;

		CryptoException failure = null;
		try {
			Pgp.decrypt(new ByteArrayInputStream(message), new ByteArrayOutputStream(),
					TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE));
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

	// ---- what is written

	@Test
	void messagesAreEncryptedWithAes256AndIntegrityProtected() throws Exception {
		byte[] message = encrypt(TestPgpKeys.RSA.publicKeys());
		PGPSecretKeyRing ring = new JcaPGPSecretKeyRingCollection(PGPUtil.getDecoderStream(
				new ByteArrayInputStream(TestPgpKeys.RSA.secretArmored().getBytes(StandardCharsets.US_ASCII))))
			.getKeyRings()
			.next();
		PGPPrivateKey privateKey = ring.getSecretKey(TestPgpKeys.RSA.encryptionKeyIds()[0])
			.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(BC).build())
				.setProvider(BC)
				.build(TestPgpKeys.PASSPHRASE));

		PGPPublicKeyEncryptedData encrypted = (PGPPublicKeyEncryptedData) list(message).get(0);

		assertThat(encrypted.isIntegrityProtected()).isTrue();
		assertThat(encrypted.getSymmetricAlgorithm(
				new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(BC).setContentProvider(BC).build(privateKey)))
			.isEqualTo(SymmetricKeyAlgorithmTags.AES_256);
	}

	@Test
	void signaturesAreMadeWithSha256ForRsaAndSha512ForOtherKeys() throws Exception {
		for (TestPgpKeys.TestKey key : new TestPgpKeys.TestKey[] { TestPgpKeys.RSA, TestPgpKeys.EC }) {
			ByteArrayOutputStream signed = new ByteArrayOutputStream();
			Pgp.sign(new ByteArrayInputStream(DATA), signed, key.secretKeys(TestPgpKeys.PASSPHRASE));

			PGPOnePassSignatureList signatures = (PGPOnePassSignatureList) new JcaPGPObjectFactory(
					PGPUtil.getDecoderStream(new ByteArrayInputStream(signed.toByteArray())))
				.nextObject();
			PGPOnePassSignature signature = signatures.get(0);
			assertThat(signature.getHashAlgorithm())
				.isEqualTo((key == TestPgpKeys.RSA) ? HashAlgorithmTags.SHA256 : HashAlgorithmTags.SHA512);
		}
	}

}
