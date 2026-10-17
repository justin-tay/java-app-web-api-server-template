package com.example.commons.crypto.openpgp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

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
		List<PGPPublicKey> keys = new ArrayList<>();
		for (PgpPublicKeys recipient : request.recipients()) {
			List<PGPPublicKey> recipientKeys = recipient.encryptionKeys();
			if (recipientKeys.isEmpty()) {
				throw new CryptoException("A recipient has no usable encryption key; "
						+ "its keys may be revoked, expired or only for signing");
			}
			keys.addAll(recipientKeys);
		}
		try {
			PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(
					new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true)
						.setSecureRandom(RANDOM)
						.setProvider(PgpProviders.BC));
			for (PGPPublicKey key : keys) {
				generator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(PgpProviders.BC));
			}
			OutputStream target = request.armored() ? new ArmoredOutputStream(new UnclosableOutputStream(out))
					: new UnclosableOutputStream(out);
			try (OutputStream encrypted = generator.open(target, new byte[BUFFER_SIZE]);
					OutputStream literal = new PGPLiteralDataGenerator().open(encrypted, PGPLiteralData.BINARY,
							PGPLiteralData.CONSOLE, PGPLiteralData.NOW, new byte[BUFFER_SIZE])) {
				in.transferTo(literal);
			}
			target.close();
		}
		catch (IOException | PGPException ex) {
			throw new CryptoException("Cannot encrypt the message", ex);
		}
	}

}
