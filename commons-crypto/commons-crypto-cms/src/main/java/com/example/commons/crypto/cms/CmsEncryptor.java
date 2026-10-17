package com.example.commons.crypto.cms;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import com.example.commons.crypto.TokenFailures;
import com.example.commons.crypto.Streams;
import com.example.commons.crypto.BouncyCastle;
import com.example.commons.crypto.CryptoLog;
import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureMode;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAESOAEPparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedDataStreamGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.PasswordRecipient;
import org.bouncycastle.cms.RecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyAgreeRecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JcePasswordRecipientInfoGenerator;
import org.bouncycastle.operator.OutputAEADEncryptor;

/**
 * Encrypts data as CMS {@code AuthEnvelopedData} with AES-256-GCM. An RSA recipient gets
 * the content key under OAEP with SHA-256, and an EC recipient gets it from ECDH with the
 * X9.63 key derivation function using SHA-256 and AES-256 key wrap. The data is streamed.
 */
public final class CmsEncryptor {

	private static final SecureRandom RANDOM = new SecureRandom();

	private CmsEncryptor() {
	}

	public static CmsEncryptor create() {
		return new CmsEncryptor();
	}

	/**
	 * Encrypts everything read from {@code in} for the recipients and writes the message
	 * to {@code out}. Neither stream is closed.
	 * @param request the recipients and output format
	 * @param in the data to encrypt
	 * @param out where the message is written
	 * @throws CryptoException if a recipient key is not supported or encryption fails
	 */
	public void encrypt(EncryptRequest request, InputStream in, OutputStream out) {
		CryptoLog.run("encrypt_message", "cms", () -> encryptLogged(request, in, out));
	}

	private void encryptLogged(EncryptRequest request, InputStream in, OutputStream out) {
		try (OutputStream encrypted = open(request, out)) {
			in.transferTo(encrypted);
		}
		catch (IOException | CMSException | GeneralSecurityException ex) {
			throw new CryptoException("Cannot encrypt the message", ex);
		}
	}

	/**
	 * Signs everything read from {@code in} and encrypts the signed message for the
	 * recipients: the signature is made first and is inside the encryption, so it is not
	 * visible to anyone who cannot decrypt. The signature is embedded and DER; the output
	 * format is the encrypt request's. Neither stream is closed.
	 * @param sign the signers
	 * @param encrypt the recipients and output format
	 * @param in the data to sign and encrypt
	 * @param out where the message is written
	 * @throws IllegalArgumentException if the signature is not embedded
	 * @throws CryptoException if a key type is not supported or the operation fails
	 */
	public void signAndEncrypt(SignRequest sign, EncryptRequest encrypt, InputStream in, OutputStream out) {
		CryptoLog.run("sign_and_encrypt_message", "cms", () -> signAndEncryptLogged(sign, encrypt, in, out));
	}

	private void signAndEncryptLogged(SignRequest sign, EncryptRequest encrypt, InputStream in, OutputStream out) {
		if (sign.mode() != SignatureMode.EMBEDDED) {
			throw new IllegalArgumentException("Signing and encrypting needs an embedded signature");
		}
		try (OutputStream encrypted = open(encrypt, out)) {
			CmsSigner.create().sign(sign.withoutArmor(), in, encrypted);
		}
		catch (IOException | CMSException | GeneralSecurityException ex) {
			throw TokenFailures.translate(ex, "Cannot sign and encrypt the message");
		}
		catch (RuntimeException ex) {
			throw TokenFailures.translate(ex);
		}
	}

	/**
	 * Opens the message and returns the stream to write the content to; closing it ends
	 * the message.
	 */
	private static OutputStream open(EncryptRequest request, OutputStream out)
			throws IOException, CMSException, GeneralSecurityException {
		CMSAuthEnvelopedDataStreamGenerator generator = new CMSAuthEnvelopedDataStreamGenerator();
		for (CmsRecipient recipient : request.recipients()) {
			generator.addRecipientInfoGenerator(recipientInfoGenerator(recipient.certificate()));
		}
		char[] password = request.password();
		if (password != null) {
			generator.addRecipientInfoGenerator(passwordRecipient(password, request.passwordIterations()));
		}
		OutputAEADEncryptor encryptor = (OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM)
			.setProvider(BouncyCastle.provider())
			.build();
		OutputStream target = request.armored() ? new ArmorOutputStream(out) : Streams.unclosable(out);
		OutputStream encrypted;
		try {
			// The key is derived from the password here, so the password is kept until
			// now.
			encrypted = generator.open(target, encryptor);
		}
		finally {
			if (password != null) {
				Arrays.fill(password, '\0');
			}
		}
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

	private static RecipientInfoGenerator passwordRecipient(char[] password, int iterations) {
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		JcePasswordRecipientInfoGenerator recipient = new JcePasswordRecipientInfoGenerator(CMSAlgorithm.AES256_CBC,
				password)
			.setProvider(BouncyCastle.provider());
		recipient.setPasswordConversionScheme(PasswordRecipient.PKCS5_SCHEME2_UTF8);
		recipient.setPRF(PasswordRecipient.PRF.HMacSHA256);
		recipient.setSaltAndIterationCount(salt, iterations);
		recipient.setSecureRandom(RANDOM);
		return recipient;
	}

	private static RecipientInfoGenerator recipientInfoGenerator(X509Certificate certificate)
			throws GeneralSecurityException {
		PublicKey publicKey = certificate.getPublicKey();
		if (publicKey instanceof ECPublicKey ecKey) {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastle.provider());
			generator.initialize(ecKey.getParams());
			KeyPair ephemeral = generator.generateKeyPair();
			JceKeyAgreeRecipientInfoGenerator agreement = new JceKeyAgreeRecipientInfoGenerator(
					CMSAlgorithm.ECDH_SHA256KDF, ephemeral.getPrivate(), ephemeral.getPublic(),
					CMSAlgorithm.AES256_WRAP)
				.setProvider(BouncyCastle.provider());
			agreement.addRecipient(certificate);
			return agreement;
		}
		if ("RSA".equals(publicKey.getAlgorithm())) {
			return new JceKeyTransRecipientInfoGenerator(certificate, oaepSha256())
				.setProvider(BouncyCastle.provider());
		}
		throw new CryptoException("Unsupported recipient key type " + publicKey.getAlgorithm());
	}

	private static AlgorithmIdentifier oaepSha256() {
		AlgorithmIdentifier sha256 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
		return new AlgorithmIdentifier(PKCSObjectIdentifiers.id_RSAES_OAEP,
				new RSAESOAEPparams(sha256, new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, sha256),
						new AlgorithmIdentifier(PKCSObjectIdentifiers.id_pSpecified, new DEROctetString(new byte[0]))));
	}

}
