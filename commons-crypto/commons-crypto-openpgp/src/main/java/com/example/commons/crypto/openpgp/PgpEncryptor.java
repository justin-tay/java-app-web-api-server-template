package com.example.commons.crypto.openpgp;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import com.example.commons.crypto.TokenFailures;
import com.example.commons.crypto.Streams;
import com.example.commons.crypto.BouncyCastle;
import com.example.commons.crypto.CryptoException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

/**
 * Encrypts data as an OpenPGP message with AES-256 and a modification detection code.
 * Each recipient gets the session key under every one of its encryption subkeys. The data
 * is streamed.
 */
public final class PgpEncryptor {

	private static final int BUFFER_SIZE = 1 << 16;

	private static final SecureRandom RANDOM = new SecureRandom();

	private PgpEncryptor() {
	}

	public static PgpEncryptor create() {
		return new PgpEncryptor();
	}

	/**
	 * Encrypts everything read from {@code in} for the recipients and writes the message
	 * to {@code out}. Neither stream is closed.
	 * @param request the recipients and output format
	 * @param in the data to encrypt
	 * @param out where the message is written
	 * @throws CryptoException if a recipient has no key that may encrypt, or encryption
	 * fails
	 */
	public void encrypt(EncryptRequest request, InputStream in, OutputStream out) {
		try (OutputStream encrypted = open(request, out);
				OutputStream literal = new PGPLiteralDataGenerator().open(encrypted, PGPLiteralData.BINARY,
						PGPLiteralData.CONSOLE, PGPLiteralData.NOW, new byte[BUFFER_SIZE])) {
			in.transferTo(literal);
		}
		catch (IOException | PGPException ex) {
			throw new CryptoException("Cannot encrypt the message", ex);
		}
	}

	/**
	 * Signs everything read from {@code in} and encrypts the signed message for the
	 * recipients: the signature is made first and is inside the encryption, so it is not
	 * visible to anyone who cannot decrypt. The signature is embedded; the output format
	 * is the encrypt request's. Neither stream is closed.
	 * @param sign the signers
	 * @param encrypt the recipients and output format
	 * @param in the data to sign and encrypt
	 * @param out where the message is written
	 * @throws IllegalArgumentException if the signature is not embedded
	 * @throws com.example.commons.crypto.PinRequiredException if a passphrase is wrong
	 * @throws CryptoException if a key may not be used or the operation fails
	 */
	public void signAndEncrypt(SignRequest sign, EncryptRequest encrypt, InputStream in, OutputStream out) {
		try (OutputStream encrypted = open(encrypt, out)) {
			PgpSigner.create().signInto(sign, in, encrypted);
		}
		catch (IOException | PGPException ex) {
			throw TokenFailures.translate(ex, "Cannot sign and encrypt the message");
		}
		catch (RuntimeException ex) {
			throw TokenFailures.translate(ex);
		}
	}

	/**
	 * Opens the message and returns the stream to write its packets to; closing it ends
	 * the message.
	 */
	private static OutputStream open(EncryptRequest request, OutputStream out) throws IOException, PGPException {
		List<PGPPublicKey> keys = new ArrayList<>();
		for (PgpPublicKeys recipient : request.recipients()) {
			List<PGPPublicKey> recipientKeys = recipient.encryptionKeys();
			if (recipientKeys.isEmpty()) {
				throw new CryptoException("A recipient has no usable encryption key; "
						+ "its keys may be revoked, expired or only for signing");
			}
			keys.addAll(recipientKeys);
		}
		PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(
				new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true)
					.setSecureRandom(RANDOM)
					.setProvider(BouncyCastle.provider()));
		for (PGPPublicKey key : keys) {
			generator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(BouncyCastle.provider()));
		}
		OutputStream target = request.armored() ? new ArmoredOutputStream(Streams.unclosable(out))
				: Streams.unclosable(out);
		OutputStream encrypted = generator.open(target, new byte[BUFFER_SIZE]);
		return new FilterOutputStream(encrypted) {
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				this.out.write(b, off, len);
			}

			@Override
			public void close() throws IOException {
				try {
					this.out.close();
				}
				finally {
					target.close();
				}
			}
		};
	}

}
