package com.example.commons.crypto.openpgp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import com.example.commons.crypto.TokenUnavailableException;
import com.example.commons.crypto.TokenFailures;
import com.example.commons.crypto.Streams;
import com.example.commons.crypto.BouncyCastle;
import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.pkcs11.Pkcs11PrivateKeyHandle;
import com.example.commons.crypto.VerifyResult;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPMarker;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PBEDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
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
	 * Neither stream is closed. A message that hides its recipient is tried with every
	 * key that can decrypt.
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
		Opened opened = open(request, in);
		try {
			copyLiteralData(new JcaPGPObjectFactory(opened.clear()), Streams.output(out, request.maxOutputBytes()), 0);
			checkIntegrity(opened.encrypted());
		}
		catch (IOException | PGPException | RuntimeException ex) {
			throw corrupt(ex);
		}
	}

	/**
	 * Decrypts a message that was signed and then encrypted, writes the signed content to
	 * {@code out}, and checks the signatures inside it. The data is streamed, so the
	 * content is written before the integrity check and the signatures are checked: do
	 * not use it unless the result is acceptable, and discard it if this throws. A
	 * message that is not signed gives a result with no signature. Neither stream is
	 * closed.
	 * @param decrypt the keys and policy for decrypting
	 * @param verify the public keys and policy for the signatures
	 * @param in the message
	 * @param out where the signed content is written
	 * @return one result per signature
	 * @throws NoMatchingKeyException if no supplied key is a recipient of the message
	 * @throws com.example.commons.crypto.PinRequiredException if a passphrase is wrong
	 * @throws IntegrityException if the message was altered or is corrupt
	 * @throws CryptoException if the input is not an encrypted message, or is refused by
	 * a policy
	 */
	public VerifyResult decryptAndVerify(DecryptRequest decrypt, VerifyRequest verify, InputStream in,
			OutputStream out) {
		Opened opened = open(decrypt, in);
		try {
			VerifyResult result = PgpVerifier.verifyPackets(verify, new JcaPGPObjectFactory(opened.clear()),
					Streams.output(out, decrypt.maxOutputBytes()));
			checkIntegrity(opened.encrypted());
			return result;
		}
		catch (IOException | PGPException | RuntimeException ex) {
			throw corrupt(ex);
		}
	}

	/**
	 * An encrypted message whose session key has been recovered, and its decrypted
	 * packets.
	 */
	private record Opened(PGPEncryptedData encrypted, InputStream clear) {
	}

	private static Opened open(DecryptRequest request, InputStream in) {
		if (request.keys() != null) {
			request.keys().ensureOpen();
		}
		char[] passphrase = request.passphrase();
		try {
			PGPEncryptedDataList list = encryptedDataList(new JcaPGPObjectFactory(PGPUtil.getDecoderStream(in)));
			for (Iterator<?> it = list.getEncryptedDataObjects(); it.hasNext();) {
				Object candidate = it.next();
				if (candidate instanceof PGPPublicKeyEncryptedData encrypted && request.keys() != null) {
					Opened opened = openWithKeys(request, encrypted);
					if (opened != null) {
						return opened;
					}
				}
				else if (candidate instanceof PGPPBEEncryptedData encrypted && passphrase != null) {
					return openWithPassphrase(request, encrypted, passphrase);
				}
			}
			throw new NoMatchingKeyException("The message is not encrypted for the supplied keys or passphrase");
		}
		catch (IOException | PGPException | RuntimeException ex) {
			Optional<TokenUnavailableException> gone = TokenFailures.unavailable(ex);
			if (gone.isPresent()) {
				throw gone.get();
			}
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new CryptoException("Cannot read the OpenPGP message", ex);
		}
		finally {
			if (passphrase != null) {
				Arrays.fill(passphrase, '\0');
			}
		}
	}

	/**
	 * Opens the part of the message that is for a key, or returns null if none of the
	 * keys is for it. A message that hides its recipient (key ID 0) is tried with every
	 * key that can decrypt.
	 */
	private static Opened openWithKeys(DecryptRequest request, PGPPublicKeyEncryptedData encrypted)
			throws IOException, PGPException {
		if (!encrypted.getKeyIdentifier().isWildcard()) {
			PGPPrivateKey privateKey = request.keys().privateKey(encrypted.getKeyID());
			return (privateKey != null) ? openWith(request, encrypted, privateKey) : null;
		}
		for (PGPPrivateKey privateKey : request.keys().encryptionKeys()) {
			try {
				return openWith(request, encrypted, privateKey);
			}
			catch (PGPException ex) {
				// Not this key: the session key does not unwrap with it.
			}
		}
		return null;
	}

	private static Opened openWith(DecryptRequest request, PGPPublicKeyEncryptedData encrypted,
			PGPPrivateKey privateKey) throws PGPException {
		PublicKeyDataDecryptorFactory factory = decryptorFactory(request, encrypted, privateKey);
		enforcePolicy(request, encrypted, encrypted.getSymmetricAlgorithm(factory));
		return new Opened(encrypted, encrypted.getDataStream(factory));
	}

	private static Opened openWithPassphrase(DecryptRequest request, PGPPBEEncryptedData encrypted, char[] passphrase)
			throws PGPException {
		try {
			PBEDataDecryptorFactory factory = new JcePBEDataDecryptorFactoryBuilder(
					new JcaPGPDigestCalculatorProviderBuilder().setProvider(BouncyCastle.provider()).build())
				.setProvider(BouncyCastle.provider())
				.build(passphrase);
			// The cipher of the passphrase packet: Bouncy Castle's own lookup of the data
			// cipher takes the first byte of the key for it when the packet has no
			// encrypted session key, which is what the usual passphrase message has.
			enforcePolicy(request, encrypted, encrypted.getAlgorithm());
			return new Opened(encrypted, encrypted.getDataStream(factory));
		}
		catch (PGPException ex) {
			throw new PinRequiredException("The passphrase is wrong", ex);
		}
	}

	private static void checkIntegrity(PGPEncryptedData encrypted) throws IOException, PGPException {
		if (encrypted.isIntegrityProtected() && !encrypted.verify()) {
			throw new IntegrityException("The message failed its integrity check");
		}
	}

	/**
	 * A failure while reading decrypted data means the message is corrupt or was altered.
	 */
	private static CryptoException corrupt(Exception ex) {
		Optional<TokenUnavailableException> gone = TokenFailures.unavailable(ex);
		if (gone.isPresent()) {
			return gone.get();
		}
		if (ex instanceof CryptoException cryptoException) {
			return cryptoException;
		}
		return new IntegrityException("The message is corrupt or was altered", ex);
	}

	/**
	 * The factory that unwraps the session key. A software key is used through Bouncy
	 * Castle. An RSA token key is used through the token's provider. An elliptic curve
	 * token key is used through the token's adapter provider, because Bouncy Castle asks
	 * for a key agreement with the key derivation built in, which SunPKCS11 lacks.
	 */
	private static PublicKeyDataDecryptorFactory decryptorFactory(DecryptRequest request,
			PGPPublicKeyEncryptedData encrypted, PGPPrivateKey privateKey) {
		JcePublicKeyDataDecryptorFactoryBuilder builder = new JcePublicKeyDataDecryptorFactoryBuilder();
		PrivateKeyHandle handle = request.keys().token().orElse(null);
		// setProvider resets the content provider, so the content provider is set after
		// it.
		if (handle == null) {
			builder.setProvider(BouncyCastle.provider());
		}
		else if (handle instanceof Pkcs11PrivateKeyHandle) {
			// A token key is used through the token's adapter provider: for an elliptic
			// curve key
			// it adds the key derivation function that Bouncy Castle asks for, and for an
			// RSA key
			// it pads the encrypted session key, which OpenPGP stores without leading
			// zeros.
			builder.setProvider(((Pkcs11PrivateKeyHandle) handle).adapterProvider());
		}
		else {
			// Any other handle: its own provider does everything, or Bouncy Castle if it
			// has none.
			builder.setProvider(handle.provider().orElse(BouncyCastle.provider()));
		}
		return builder.setContentProvider(BouncyCastle.provider()).build(privateKey);
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

	private static void enforcePolicy(DecryptRequest request, PGPEncryptedData encrypted, int cipher) {
		if (request.legacyCompatibility()) {
			return;
		}
		if (!encrypted.isIntegrityProtected()) {
			throw new CryptoException(
					"The message is not integrity protected; " + "enable legacy compatibility to accept it");
		}
		if (!STRONG_CIPHERS.contains(cipher)) {
			throw new CryptoException("The message uses a weak cipher; enable legacy compatibility to accept it");
		}
	}

	private static void copyLiteralData(JcaPGPObjectFactory factory, OutputStream out, int nesting)
			throws IOException, PGPException {
		for (Object object = factory.nextObject(); object != null; object = factory.nextObject()) {
			if (object instanceof PGPCompressedData compressed) {
				if (nesting >= PgpLimits.MAX_COMPRESSION_NESTING) {
					throw PgpLimits.nestedTooDeeply();
				}
				copyLiteralData(new JcaPGPObjectFactory(compressed.getDataStream()), out, nesting + 1);
			}
			else if (object instanceof PGPLiteralData literal) {
				try (InputStream data = literal.getInputStream()) {
					data.transferTo(out);
				}
			}
		}
	}

}
