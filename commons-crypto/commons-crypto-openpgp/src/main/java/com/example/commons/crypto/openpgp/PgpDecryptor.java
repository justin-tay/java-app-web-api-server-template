package com.example.commons.crypto.openpgp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPMarker;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;

/**
 * Decrypts OpenPGP messages, armored or binary. The data is streamed, so the integrity
 * check is made after the last byte is written; when this throws
 * {@link IntegrityException}, discard what was written to the output stream. Signatures
 * inside the message are not checked.
 */
public final class PgpDecryptor {

	private static final Set<Integer> STRONG_CIPHERS = Set.of(SymmetricKeyAlgorithmTags.AES_128,
			SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_256,
			SymmetricKeyAlgorithmTags.CAMELLIA_128, SymmetricKeyAlgorithmTags.CAMELLIA_192,
			SymmetricKeyAlgorithmTags.CAMELLIA_256);

	private PgpDecryptor() {
	}

	public static PgpDecryptor create() {
		return new PgpDecryptor();
	}

	/**
	 * Decrypts the message read from {@code in} and writes the plain data to {@code out}.
	 * Neither stream is closed. Messages addressed to no particular key (wildcard
	 * recipients) are not supported.
	 * @param request the keys and policy
	 * @param in the message
	 * @param out where the plain data is written
	 * @throws NoMatchingKeyException if no supplied key is a recipient of the message
	 * @throws com.example.commons.crypto.PinRequiredException if a passphrase is wrong
	 * @throws IntegrityException if the message was altered or is corrupt
	 * @throws CryptoException if the input is not an encrypted message, or is refused by
	 * the policy
	 */
	public void decrypt(DecryptRequest request, InputStream in, OutputStream out) {
		PGPPublicKeyEncryptedData encrypted;
		PublicKeyDataDecryptorFactory factory;
		InputStream clear;
		try {
			PGPEncryptedDataList list = encryptedDataList(new JcaPGPObjectFactory(PGPUtil.getDecoderStream(in)));
			encrypted = null;
			factory = null;
			for (Iterator<?> it = list.getEncryptedDataObjects(); it.hasNext() && encrypted == null;) {
				if (it.next() instanceof PGPPublicKeyEncryptedData candidate) {
					PGPPrivateKey privateKey = request.keys().privateKey(candidate.getKeyID());
					if (privateKey != null) {
						encrypted = candidate;
						factory = new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(PgpProviders.BC)
							.setContentProvider(PgpProviders.BC)
							.build(privateKey);
					}
				}
			}
			if (encrypted == null) {
				throw new NoMatchingKeyException("The message is not encrypted for the supplied keys");
			}
			enforcePolicy(request, encrypted, factory);
			clear = encrypted.getDataStream(factory);
		}
		catch (IOException | PGPException | RuntimeException ex) {
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new CryptoException("Cannot read the OpenPGP message", ex);
		}
		try {
			copyLiteralData(new JcaPGPObjectFactory(clear), out);
			if (encrypted.isIntegrityProtected() && !encrypted.verify()) {
				throw new IntegrityException("The message failed its integrity check");
			}
		}
		catch (IOException | PGPException | RuntimeException ex) {
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new IntegrityException("The message is corrupt or was altered", ex);
		}
	}

	private static PGPEncryptedDataList encryptedDataList(JcaPGPObjectFactory factory) throws IOException {
		Object object = factory.nextObject();
		if (object instanceof PGPMarker) {
			object = factory.nextObject();
		}
		if (object instanceof PGPEncryptedDataList list) {
			return list;
		}
		throw new CryptoException("The input is not an encrypted OpenPGP message");
	}

	private static void enforcePolicy(DecryptRequest request, PGPPublicKeyEncryptedData encrypted,
			PublicKeyDataDecryptorFactory factory) throws PGPException {
		if (request.legacyCompatibility()) {
			return;
		}
		if (!encrypted.isIntegrityProtected()) {
			throw new CryptoException(
					"The message is not integrity protected; " + "enable legacy compatibility to accept it");
		}
		if (!STRONG_CIPHERS.contains(encrypted.getSymmetricAlgorithm(factory))) {
			throw new CryptoException("The message uses a weak cipher; enable legacy compatibility to accept it");
		}
	}

	private static void copyLiteralData(JcaPGPObjectFactory factory, OutputStream out)
			throws IOException, PGPException {
		for (Object object = factory.nextObject(); object != null; object = factory.nextObject()) {
			if (object instanceof PGPCompressedData compressed) {
				copyLiteralData(new JcaPGPObjectFactory(compressed.getDataStream()), out);
			}
			else if (object instanceof PGPLiteralData literal) {
				try (InputStream data = literal.getInputStream()) {
					data.transferTo(out);
				}
			}
		}
	}

}
