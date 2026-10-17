package com.example.commons.crypto.cms;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import javax.crypto.NoSuchPaddingException;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.IntegrityException;
import com.example.commons.crypto.NoMatchingKeyException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cms.CMSAuthEnvelopedDataParser;
import org.bouncycastle.cms.CMSEnvelopedDataParser;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.Recipient;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.jcajce.JceKeyAgreeAuthEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyAgreeEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyAgreeRecipient;
import org.bouncycastle.cms.jcajce.JceKeyAgreeRecipientId;
import org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;

/**
 * Decrypts CMS messages made by {@link CmsEncryptor}, as DER or PEM. The data is
 * streamed, so the integrity check is made after the last byte is written; when this
 * throws {@link IntegrityException}, discard what was written to the output stream.
 */
public final class CmsDecryptor {

	private static final int PREFIX_LENGTH = 32;

	private CmsDecryptor() {
	}

	public static CmsDecryptor create() {
		return new CmsDecryptor();
	}

	/**
	 * Decrypts the message read from {@code in} and writes the plain data to {@code out}.
	 * Neither stream is closed.
	 * @param request the key and policy
	 * @param in the message, DER or PEM
	 * @param out where the plain data is written
	 * @throws NoMatchingKeyException if the key is not one of the message's recipients
	 * @throws IntegrityException if the message was altered
	 * @throws CryptoException if the message is not supported, or is refused by the
	 * policy
	 */
	public void decrypt(DecryptRequest request, InputStream in, OutputStream out) {
		try (InputStream message = peekable(ArmorInputStream.dearmorIfNeeded(in))) {
			ASN1ObjectIdentifier contentType = contentType(message);
			if (CMSObjectIdentifiers.authEnvelopedData.equals(contentType)) {
				decryptAuthenticated(request, message, out);
			}
			else if (PKCSObjectIdentifiers.envelopedData.equals(contentType)) {
				decryptLegacy(request, message, out);
			}
			else {
				throw new CryptoException("The input is not an encrypted CMS message");
			}
		}
		catch (IOException | CMSException ex) {
			throw failure(ex);
		}
	}

	private void decryptAuthenticated(DecryptRequest request, InputStream message, OutputStream out)
			throws IOException, CMSException {
		CMSAuthEnvelopedDataParser parser = new CMSAuthEnvelopedDataParser(message);
		RecipientInformation recipient = select(request, parser.getRecipientInfos());
		copy(recipient, keyRecipient(request, true), out);
	}

	private void decryptLegacy(DecryptRequest request, InputStream message, OutputStream out)
			throws IOException, CMSException {
		if (!request.legacyCompatibility()) {
			throw new CryptoException("The message is not integrity protected (CMS EnvelopedData); "
					+ "enable legacy compatibility to accept it");
		}
		CMSEnvelopedDataParser parser = new CMSEnvelopedDataParser(message);
		RecipientInformation recipient = select(request, parser.getRecipientInfos());
		copy(recipient, keyRecipient(request, false), out);
	}

	/**
	 * The recipient that unwraps the content key with the request's key. A token key is
	 * used through the token's provider for RSA. For an EC token key no provider is
	 * forced: the key has no encoding, so Bouncy Castle is told its algorithm, and the
	 * agreement is found by key through the library's adapter provider.
	 */
	private static Recipient keyRecipient(DecryptRequest request, boolean authenticated) {
		PrivateKey key = request.key().key();
		Optional<Provider> token = request.key().provider();
		PublicKey publicKey = request.certificate().getPublicKey();
		if ("RSA".equals(publicKey.getAlgorithm())) {
			Provider provider = token.orElse(CmsProviders.BC);
			return authenticated
					? new JceKeyTransAuthEnvelopedRecipient(key).setProvider(provider)
						.setContentProvider(CmsProviders.BC)
					: new JceKeyTransEnvelopedRecipient(key).setProvider(provider).setContentProvider(CmsProviders.BC);
		}
		JceKeyAgreeRecipient recipient = authenticated ? new JceKeyAgreeAuthEnvelopedRecipient(key)
				: new JceKeyAgreeEnvelopedRecipient(key);
		if (token.isPresent()) {
			recipient.setPrivateKeyAlgorithmIdentifier(
					SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()).getAlgorithm());
		}
		else {
			recipient.setProvider(CmsProviders.BC);
		}
		recipient.setContentProvider(CmsProviders.BC);
		return recipient;
	}

	private static void copy(RecipientInformation recipient, Recipient keyRecipient, OutputStream out)
			throws IOException, CMSException {
		try (InputStream content = recipient.getContentStream(keyRecipient).getContentStream()) {
			content.transferTo(out);
		}
	}

	private RecipientInformation select(DecryptRequest request, RecipientInformationStore store) {
		boolean rsa = request.certificate().getPublicKey().getAlgorithm().equals("RSA");
		RecipientId id = rsa ? new JceKeyTransRecipientId(request.certificate())
				: new JceKeyAgreeRecipientId(request.certificate());
		Collection<RecipientInformation> matches = store.getRecipients(id);
		if (matches.isEmpty()) {
			throw new NoMatchingKeyException("The message is not encrypted for the supplied key");
		}
		RecipientInformation recipient = matches.iterator().next();
		if (PKCSObjectIdentifiers.rsaEncryption.equals(recipient.getKeyEncryptionAlgorithm().getAlgorithm())
				&& !request.legacyCompatibility() && !request.key().pkcs1v15KeyTransportAllowed()) {
			throw new CryptoException("The message uses PKCS#1 v1.5 key transport; enable legacy compatibility, "
					+ "or allow it on a token key handle, to accept it");
		}
		return recipient;
	}

	private static InputStream peekable(InputStream in) {
		return (in instanceof BufferedInputStream) ? in : new BufferedInputStream(in, 8192);
	}

	/**
	 * Reads the content type OID from the start of the ContentInfo without consuming it.
	 */
	private static ASN1ObjectIdentifier contentType(InputStream message) throws IOException {
		message.mark(PREFIX_LENGTH);
		byte[] prefix = message.readNBytes(PREFIX_LENGTH);
		message.reset();
		try {
			if (prefix.length < 4 || prefix[0] != 0x30) {
				throw new CryptoException("The input is not a CMS message");
			}
			int index = 2;
			if ((prefix[1] & 0x80) != 0 && (prefix[1] & 0x7f) > 0) {
				index += prefix[1] & 0x7f;
			}
			if (prefix[index] != 0x06) {
				throw new CryptoException("The input is not a CMS message");
			}
			int length = prefix[index + 1];
			return ASN1ObjectIdentifier.getInstance(Arrays.copyOfRange(prefix, index, index + 2 + length));
		}
		catch (IndexOutOfBoundsException | IllegalArgumentException ex) {
			throw new CryptoException("The input is not a CMS message", ex);
		}
	}

	private static CryptoException failure(Exception ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof NoSuchPaddingException && String.valueOf(cause.getMessage()).contains("OAEP")) {
				return new CryptoException("The message needs RSA OAEP, which the provider of the key does not "
						+ "offer (SunPKCS11 has none); use a provider that supports OAEP, or allow PKCS#1 v1.5 "
						+ "on the token key handle if the sender can use it", ex);
			}
			String name = cause.getClass().getName();
			String message = String.valueOf(cause.getMessage()).toLowerCase(java.util.Locale.ROOT);
			if (name.contains("InvalidCipherText") || name.contains("AEADBadTag") || name.contains("BadPadding")
					|| message.contains("mac") || message.contains("tag mismatch")) {
				return new IntegrityException("The message failed its integrity check", ex);
			}
		}
		return new CryptoException("Cannot decrypt the message", ex);
	}

}
