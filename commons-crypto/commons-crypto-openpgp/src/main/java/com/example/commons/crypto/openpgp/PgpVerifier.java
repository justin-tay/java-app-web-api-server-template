package com.example.commons.crypto.openpgp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureResult;
import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

/**
 * Verifies OpenPGP signatures: embedded, detached and cleartext, armored or binary. It
 * checks each signature against the public keys the caller supplies and reports the
 * outcome per signature; the caller decides whether a valid signer is trusted. The data
 * is streamed, so for an embedded message the content has already been written when the
 * signatures are checked: do not use it unless the result is acceptable.
 */
public final class PgpVerifier {

	private static final Set<Integer> WEAK_HASHES = Set.of(HashAlgorithmTags.MD5, HashAlgorithmTags.SHA1,
			HashAlgorithmTags.MD2);

	private static final int BUFFER_SIZE = 1 << 16;

	private PgpVerifier() {
	}

	public static PgpVerifier create() {
		return new PgpVerifier();
	}

	/**
	 * Verifies a message with an embedded or cleartext signature and writes its content
	 * to {@code content}. A cleartext message is written with LF line endings and without
	 * a line ending after the last line. Neither stream is closed.
	 * @param request the public keys and policy
	 * @param message the signed message
	 * @param content where the signed content is written
	 * @return one result per signature
	 * @throws CryptoException if the message cannot be read, has no content, or is
	 * refused by the policy
	 */
	public VerifyResult verify(VerifyRequest request, InputStream message, OutputStream content) {
		try {
			InputStream decoded = PGPUtil.getDecoderStream(message);
			if (decoded instanceof ArmoredInputStream armored && armored.isClearText()) {
				return verifyCleartext(request, armored, content);
			}
			return verifyPackets(request, new JcaPGPObjectFactory(decoded), content);
		}
		catch (IOException | PGPException | RuntimeException ex) {
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new CryptoException("Cannot read the signed message", ex);
		}
	}

	/**
	 * Verifies a detached signature against the data it was made for. Neither stream is
	 * closed.
	 * @param request the public keys and policy
	 * @param data the data that was signed
	 * @param signature the detached signature
	 * @return one result per signature
	 * @throws CryptoException if the signature cannot be read or is refused by the policy
	 */
	public VerifyResult verifyDetached(VerifyRequest request, InputStream data, InputStream signature) {
		try {
			Object object = new JcaPGPObjectFactory(PGPUtil.getDecoderStream(signature)).nextObject();
			if (!(object instanceof PGPSignatureList signatures)) {
				throw new CryptoException("The input is not an OpenPGP signature");
			}
			List<PGPPublicKey> keys = new ArrayList<>();
			for (PGPSignature candidate : signatures) {
				checkHash(request, candidate.getHashAlgorithm());
				PGPPublicKey key = request.find(candidate.getKeyID());
				if (key != null) {
					candidate.init(new JcaPGPContentVerifierBuilderProvider().setProvider(PgpProviders.BC), key);
				}
				keys.add(key);
			}
			byte[] buffer = new byte[BUFFER_SIZE];
			for (int read = data.read(buffer); read != -1; read = data.read(buffer)) {
				for (int i = 0; i < signatures.size(); i++) {
					if (keys.get(i) != null) {
						signatures.get(i).update(buffer, 0, read);
					}
				}
			}
			List<SignatureResult> results = new ArrayList<>();
			for (int i = 0; i < signatures.size(); i++) {
				results.add(result(signatures.get(i), keys.get(i)));
			}
			return new VerifyResult(results);
		}
		catch (IOException | PGPException | RuntimeException ex) {
			if (ex instanceof CryptoException cryptoException) {
				throw cryptoException;
			}
			throw new CryptoException("Cannot read the signature", ex);
		}
	}

	static VerifyResult verifyPackets(VerifyRequest request, JcaPGPObjectFactory factory, OutputStream content)
			throws IOException, PGPException {
		List<PGPOnePassSignature> onePass = new ArrayList<>();
		List<PGPPublicKey> keys = new ArrayList<>();
		boolean sawContent = false;
		PGPSignatureList signatures = null;
		for (Object object = factory.nextObject(); object != null; object = factory.nextObject()) {
			if (object instanceof PGPCompressedData compressed) {
				factory = new JcaPGPObjectFactory(compressed.getDataStream());
			}
			else if (object instanceof PGPOnePassSignatureList list) {
				for (PGPOnePassSignature signature : list) {
					checkHash(request, signature.getHashAlgorithm());
					PGPPublicKey key = request.find(signature.getKeyID());
					if (key != null) {
						signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(PgpProviders.BC), key);
					}
					onePass.add(signature);
					keys.add(key);
				}
			}
			else if (object instanceof PGPLiteralData literal) {
				sawContent = true;
				copyAndUpdate(literal.getInputStream(), content, onePass, keys);
			}
			else if (object instanceof PGPSignatureList list) {
				if (!sawContent) {
					throw new CryptoException("The input has no embedded content; it is a detached signature, "
							+ "so verify it against the data with verifyDetached");
				}
				signatures = list;
			}
		}
		if (!sawContent) {
			throw new CryptoException("The input is not a signed OpenPGP message");
		}
		List<SignatureResult> results = new ArrayList<>();
		for (int i = 0; i < onePass.size(); i++) {
			PGPOnePassSignature signature = onePass.get(i);
			PGPSignature matching = find(signatures, signature.getKeyID());
			Instant time = (matching != null) ? time(matching) : null;
			if (keys.get(i) == null) {
				results.add(new SignatureResult(describe(signature.getKeyID()), SignatureStatus.UNKNOWN_SIGNER, time));
			}
			else {
				boolean valid = matching != null && signature.verify(matching);
				results.add(new SignatureResult(describe(signature.getKeyID()),
						valid ? SignatureStatus.VALID : SignatureStatus.INVALID, time));
			}
		}
		return new VerifyResult(results);
	}

	private static void copyAndUpdate(InputStream data, OutputStream content, List<PGPOnePassSignature> onePass,
			List<PGPPublicKey> keys) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		for (int read = data.read(buffer); read != -1; read = data.read(buffer)) {
			content.write(buffer, 0, read);
			for (int i = 0; i < onePass.size(); i++) {
				if (keys.get(i) != null) {
					onePass.get(i).update(buffer, 0, read);
				}
			}
		}
	}

	private static VerifyResult verifyCleartext(VerifyRequest request, ArmoredInputStream armored, OutputStream content)
			throws IOException, PGPException {
		ByteArrayOutputStream text = new ByteArrayOutputStream();
		for (int next = armored.read(); next >= 0 && armored.isClearText(); next = armored.read()) {
			text.write(next);
		}
		List<String> lines = PgpText.lines(text.toString(StandardCharsets.UTF_8));
		content.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		byte[] canonical = PgpText.canonical(lines);
		Object object = new JcaPGPObjectFactory(armored).nextObject();
		if (!(object instanceof PGPSignatureList signatures)) {
			throw new CryptoException("The cleartext message has no signature");
		}
		List<SignatureResult> results = new ArrayList<>();
		for (PGPSignature signature : signatures) {
			checkHash(request, signature.getHashAlgorithm());
			PGPPublicKey key = request.find(signature.getKeyID());
			if (key != null) {
				signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(PgpProviders.BC), key);
				signature.update(canonical);
			}
			results.add(result(signature, key));
		}
		return new VerifyResult(results);
	}

	private static SignatureResult result(PGPSignature signature, PGPPublicKey key) throws PGPException {
		SignatureStatus status = (key == null) ? SignatureStatus.UNKNOWN_SIGNER
				: signature.verify() ? SignatureStatus.VALID : SignatureStatus.INVALID;
		return new SignatureResult(describe(signature.getKeyID()), status, time(signature));
	}

	private static PGPSignature find(PGPSignatureList signatures, long keyId) {
		if (signatures != null) {
			for (PGPSignature signature : signatures) {
				if (signature.getKeyID() == keyId) {
					return signature;
				}
			}
		}
		return null;
	}

	private static void checkHash(VerifyRequest request, int hash) {
		if (WEAK_HASHES.contains(hash) && !request.legacyCompatibility()) {
			throw new CryptoException(
					"The signature uses a weak hash (SHA-1 or older); " + "enable legacy compatibility to accept it");
		}
	}

	private static Instant time(PGPSignature signature) {
		return (signature.getCreationTime() != null) ? signature.getCreationTime().toInstant() : null;
	}

	private static String describe(long keyId) {
		return String.format("%016X", keyId);
	}

}
