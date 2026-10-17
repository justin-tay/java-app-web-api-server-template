package com.example.commons.crypto.cms;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;

/**
 * One-line forms of the common CMS operations, for the defaults: DER output, and no
 * legacy compatibility. Use the requests with {@link CmsEncryptor}, {@link CmsDecryptor},
 * {@link CmsSigner} and {@link CmsVerifier} for anything else. No stream is closed.
 */
public final class Cms {

	private Cms() {
	}

	/**
	 * Encrypts {@code in} for the recipients.
	 * @param in the data
	 * @param out where the message is written
	 * @param recipients the recipients' certificates
	 */
	public static void encrypt(InputStream in, OutputStream out, X509Certificate... recipients) {
		EncryptRequest.Builder request = EncryptRequest.builder();
		for (X509Certificate recipient : recipients) {
			request.recipients(CmsRecipient.certificate(recipient));
		}
		CmsEncryptor.create().encrypt(request.build(), in, out);
	}

	/**
	 * Decrypts a message.
	 * @param in the message
	 * @param out where the plain data is written
	 * @param key the recipient's key
	 * @param certificate the certificate of the key
	 */
	public static void decrypt(InputStream in, OutputStream out, PrivateKeyHandle key, X509Certificate certificate) {
		CmsDecryptor.create().decrypt(DecryptRequest.builder().key(key, certificate).build(), in, out);
	}

	/**
	 * Signs {@code in}, with the signature embedded in the output.
	 * @param in the data
	 * @param out where the signed message is written
	 * @param key the signing key
	 * @param certificate the certificate of the key
	 */
	public static void sign(InputStream in, OutputStream out, PrivateKeyHandle key, X509Certificate certificate) {
		CmsSigner.create().sign(SignRequest.builder().signer(key, certificate).build(), in, out);
	}

	/**
	 * Signs {@code in}, writing only the signature to the output.
	 * @param in the data
	 * @param out where the detached signature is written
	 * @param key the signing key
	 * @param certificate the certificate of the key
	 */
	public static void signDetached(InputStream in, OutputStream out, PrivateKeyHandle key,
			X509Certificate certificate) {
		CmsSigner.create()
			.sign(SignRequest.builder().signer(key, certificate).mode(SignatureMode.DETACHED).build(), in, out);
	}

	/**
	 * Verifies a message with an embedded signature.
	 * @param message the signed message
	 * @param content where the signed content is written
	 * @param signers the certificates of the signers the caller knows
	 * @return one result per signature
	 */
	public static VerifyResult verify(InputStream message, OutputStream content, X509Certificate... signers) {
		return CmsVerifier.create().verify(VerifyRequest.builder().certificates(signers).build(), message, content);
	}

	/**
	 * Verifies a detached signature.
	 * @param data the data that was signed
	 * @param signature the detached signature
	 * @param signers the certificates of the signers the caller knows
	 * @return one result per signature
	 */
	public static VerifyResult verifyDetached(InputStream data, InputStream signature, X509Certificate... signers) {
		return CmsVerifier.create()
			.verifyDetached(VerifyRequest.builder().certificates(signers).build(), data, signature);
	}

}
