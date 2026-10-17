package com.example.commons.crypto.openpgp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;

/**
 * One-line forms of the common OpenPGP operations, for the defaults: binary output, and
 * no legacy compatibility. Use the requests with {@link PgpEncryptor},
 * {@link PgpDecryptor}, {@link PgpSigner} and {@link PgpVerifier} for anything else. No
 * stream is closed.
 */
public final class Pgp {

	private Pgp() {
	}

	/**
	 * Encrypts {@code in} for the recipients.
	 * @param in the data
	 * @param out where the message is written
	 * @param recipients the recipients' public keys
	 */
	public static void encrypt(InputStream in, OutputStream out, PgpPublicKeys... recipients) {
		PgpEncryptor.create().encrypt(EncryptRequest.builder().recipients(recipients).build(), in, out);
	}

	/**
	 * Decrypts a message.
	 * @param in the message
	 * @param out where the plain data is written
	 * @param keys the recipient's secret keys
	 */
	public static void decrypt(InputStream in, OutputStream out, PgpSecretKeys keys) {
		PgpDecryptor.create().decrypt(DecryptRequest.builder().keys(keys).build(), in, out);
	}

	/**
	 * Decrypts a message into memory. Unlike {@link #decrypt}, nothing is returned unless
	 * the whole message passed its integrity check, so no unauthenticated data can be
	 * used by mistake.
	 * @param in the message
	 * @param keys the recipient's secret keys
	 * @param maxBytes the most plain data to accept, which bounds the memory used
	 * @return the plain data
	 */
	public static byte[] decryptToBytes(InputStream in, PgpSecretKeys keys, long maxBytes) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpDecryptor.create().decrypt(DecryptRequest.builder().keys(keys).maxOutputBytes(maxBytes).build(), in, out);
		return out.toByteArray();
	}

	/**
	 * Signs {@code in}, with the signature embedded in the output.
	 * @param in the data
	 * @param out where the signed message is written
	 * @param signer the signer's secret keys
	 */
	public static void sign(InputStream in, OutputStream out, PgpSecretKeys signer) {
		PgpSigner.create().sign(SignRequest.builder().signer(signer).build(), in, out);
	}

	/**
	 * Signs {@code in}, writing only the signature to the output.
	 * @param in the data
	 * @param out where the detached signature is written
	 * @param signer the signer's secret keys
	 */
	public static void signDetached(InputStream in, OutputStream out, PgpSecretKeys signer) {
		PgpSigner.create().sign(SignRequest.builder().signer(signer).mode(SignatureMode.DETACHED).build(), in, out);
	}

	/**
	 * Verifies a message with an embedded or cleartext signature.
	 * @param message the signed message
	 * @param content where the signed content is written
	 * @param signers the public keys of the signers the caller knows
	 * @return one result per signature
	 */
	public static VerifyResult verify(InputStream message, OutputStream content, PgpPublicKeys... signers) {
		return PgpVerifier.create().verify(VerifyRequest.builder().keys(signers).build(), message, content);
	}

	/**
	 * Verifies a detached signature.
	 * @param data the data that was signed
	 * @param signature the detached signature
	 * @param signers the public keys of the signers the caller knows
	 * @return one result per signature
	 */
	public static VerifyResult verifyDetached(InputStream data, InputStream signature, PgpPublicKeys... signers) {
		return PgpVerifier.create().verifyDetached(VerifyRequest.builder().keys(signers).build(), data, signature);
	}

}
