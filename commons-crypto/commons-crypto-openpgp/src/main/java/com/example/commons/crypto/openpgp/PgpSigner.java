package com.example.commons.crypto.openpgp;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.example.commons.crypto.CryptoException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;

/**
 * Signs data as OpenPGP, embedded, detached or as a cleartext signature, with one
 * signature per signer. RSA keys sign with SHA-256 and other keys with SHA-512. Embedded
 * and detached signatures are streamed; a cleartext signature reads the text into memory.
 */
public final class PgpSigner {

	private static final int BUFFER_SIZE = 1 << 16;

	private PgpSigner() {
	}

	public static PgpSigner create() {
		return new PgpSigner();
	}

	/**
	 * Signs everything read from {@code in}. For an embedded signature {@code out}
	 * receives the data and the signatures; for a detached signature it receives the
	 * signatures only; for a cleartext signature it receives the readable text and the
	 * signature. Neither stream is closed.
	 * @param request the signers, mode and output format
	 * @param in the data to sign
	 * @param out where the signed message or the detached signature is written
	 * @throws com.example.commons.crypto.PinRequiredException if a passphrase is wrong
	 * @throws CryptoException if a signer has no signing key or signing fails
	 */
	public void sign(SignRequest request, InputStream in, OutputStream out) {
		try {
			int signatureType = (request.form() == SignRequest.Form.CLEARTEXT) ? PGPSignature.CANONICAL_TEXT_DOCUMENT
					: PGPSignature.BINARY_DOCUMENT;
			List<Signing> signings = new ArrayList<>();
			for (PgpSecretKeys signer : request.signers()) {
				signings.add(new Signing(signer.signingKey(), signatureType));
			}
			OutputStream unclosable = new UnclosableOutputStream(out);
			OutputStream target = request.armored() ? new ArmoredOutputStream(unclosable) : unclosable;
			switch (request.form()) {
				case EMBEDDED -> signEmbedded(signings, in, target);
				case DETACHED -> signDetached(signings, in, target);
				case CLEARTEXT -> signCleartext(signings, in, (ArmoredOutputStream) target);
			}
			target.close();
		}
		catch (IOException | PGPException ex) {
			throw new CryptoException("Cannot sign the data", ex);
		}
	}

	private static void signEmbedded(List<Signing> signings, InputStream in, OutputStream target)
			throws IOException, PGPException {
		BCPGOutputStream packets = new BCPGOutputStream(target);
		for (int i = 0; i < signings.size(); i++) {
			// A one-pass signature that is "nested" is followed by another one.
			signings.get(i).generator.generateOnePassVersion(i < signings.size() - 1).encode(packets);
		}
		try (OutputStream literal = new PGPLiteralDataGenerator().open(packets, PGPLiteralData.BINARY,
				PGPLiteralData.CONSOLE, PGPLiteralData.NOW, new byte[BUFFER_SIZE])) {
			in.transferTo(new FilterOutputStream(literal) {
				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					this.out.write(b, off, len);
					signings.forEach((signing) -> signing.generator.update(b, off, len));
				}

				@Override
				public void close() {
					// The literal stream is closed by the enclosing block.
				}
			});
		}
		for (int i = signings.size() - 1; i >= 0; i--) {
			signings.get(i).generator.generate().encode(packets);
		}
		packets.finish();
	}

	private static void signDetached(List<Signing> signings, InputStream in, OutputStream target)
			throws IOException, PGPException {
		BCPGOutputStream packets = new BCPGOutputStream(target);
		byte[] buffer = new byte[BUFFER_SIZE];
		for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
			for (Signing signing : signings) {
				signing.generator.update(buffer, 0, read);
			}
		}
		for (Signing signing : signings) {
			signing.generator.generate().encode(packets);
		}
		packets.finish();
	}

	private static void signCleartext(List<Signing> signings, InputStream in, ArmoredOutputStream armored)
			throws IOException, PGPException {
		List<String> lines = PgpText.lines(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		armored.beginClearText(signings.get(0).hash);
		ByteArrayOutputStream shown = new ByteArrayOutputStream();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				shown.write('\n');
			}
			shown.write(lines.get(i).getBytes(StandardCharsets.UTF_8));
		}
		byte[] canonical = PgpText.canonical(lines);
		for (Signing signing : signings) {
			signing.generator.update(canonical);
		}
		armored.write(shown.toByteArray());
		// The line ending before the signature is not part of the signed text.
		armored.write('\n');
		armored.endClearText();
		BCPGOutputStream packets = new BCPGOutputStream(armored);
		for (Signing signing : signings) {
			signing.generator.generate().encode(packets);
		}
		packets.finish();
	}

	/** A signature generator ready to sign, with the hash it uses. */
	private static final class Signing {

		final PGPSignatureGenerator generator;

		final int hash;

		Signing(PgpSecretKeys.SigningKey key, int signatureType) throws PGPException {
			int algorithm = key.publicKey().getAlgorithm();
			this.hash = (algorithm == PublicKeyAlgorithmTags.RSA_GENERAL
					|| algorithm == PublicKeyAlgorithmTags.RSA_SIGN) ? HashAlgorithmTags.SHA256
							: HashAlgorithmTags.SHA512;
			this.generator = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(algorithm, this.hash)
				.setProvider(key.provider().orElse(PgpProviders.BC)), key.publicKey());
			this.generator.init(signatureType, key.privateKey());
		}

	}

}
