package com.example.commons.crypto.cms;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;

import com.example.commons.crypto.CryptoException;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAESOAEPparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedDataStreamGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.RecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyAgreeRecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.OutputAEADEncryptor;

/**
 * Encrypts data as CMS {@code AuthEnvelopedData} with AES-256-GCM. An RSA recipient gets
 * the content key under OAEP with SHA-256, and an EC recipient gets it from ECDH with the
 * X9.63 key derivation function using SHA-256 and AES-256 key wrap. The data is streamed.
 */
public final class CmsEncryptor {

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
		try {
			CMSAuthEnvelopedDataStreamGenerator generator = new CMSAuthEnvelopedDataStreamGenerator();
			for (CmsRecipient recipient : request.recipients()) {
				generator.addRecipientInfoGenerator(recipientInfoGenerator(recipient.certificate()));
			}
			OutputAEADEncryptor encryptor = (OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(
					CMSAlgorithm.AES256_GCM)
				.setProvider(CmsProviders.BC)
				.build();
			OutputStream target = request.armored() ? new ArmorOutputStream(out) : new Unclosable(out);
			try (OutputStream encrypted = generator.open(target, encryptor)) {
				in.transferTo(encrypted);
			}
			target.close();
		}
		catch (IOException | CMSException | GeneralSecurityException ex) {
			throw new CryptoException("Cannot encrypt the message", ex);
		}
	}

	private static RecipientInfoGenerator recipientInfoGenerator(X509Certificate certificate)
			throws GeneralSecurityException {
		PublicKey publicKey = certificate.getPublicKey();
		if (publicKey instanceof ECPublicKey ecKey) {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", CmsProviders.BC);
			generator.initialize(ecKey.getParams());
			KeyPair ephemeral = generator.generateKeyPair();
			JceKeyAgreeRecipientInfoGenerator agreement = new JceKeyAgreeRecipientInfoGenerator(
					CMSAlgorithm.ECDH_SHA256KDF, ephemeral.getPrivate(), ephemeral.getPublic(),
					CMSAlgorithm.AES256_WRAP)
				.setProvider(CmsProviders.BC);
			agreement.addRecipient(certificate);
			return agreement;
		}
		if ("RSA".equals(publicKey.getAlgorithm())) {
			return new JceKeyTransRecipientInfoGenerator(certificate, oaepSha256()).setProvider(CmsProviders.BC);
		}
		throw new CryptoException("Unsupported recipient key type " + publicKey.getAlgorithm());
	}

	private static AlgorithmIdentifier oaepSha256() {
		AlgorithmIdentifier sha256 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
		return new AlgorithmIdentifier(PKCSObjectIdentifiers.id_RSAES_OAEP,
				new RSAESOAEPparams(sha256, new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, sha256),
						new AlgorithmIdentifier(PKCSObjectIdentifiers.id_pSpecified, new DEROctetString(new byte[0]))));
	}

	/** Passes writes through and leaves the wrapped stream open when closed. */
	private static final class Unclosable extends java.io.FilterOutputStream {

		Unclosable(OutputStream out) {
			super(out);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			this.out.write(b, off, len);
		}

		@Override
		public void close() throws IOException {
			flush();
		}

	}

}
