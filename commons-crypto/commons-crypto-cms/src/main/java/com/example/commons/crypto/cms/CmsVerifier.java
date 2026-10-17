package com.example.commons.crypto.cms;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.example.commons.crypto.BouncyCastle;
import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureResult;
import com.example.commons.crypto.SignatureStatus;
import com.example.commons.crypto.VerifyResult;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.encoders.Hex;

/**
 * Verifies CMS {@code SignedData}, embedded or detached, as DER or PEM. It checks each
 * signature against the certificates the caller supplies and reports the outcome per
 * signature; it does not validate certificate chains or revocation, and the caller
 * decides whether a valid signer is trusted. The data is streamed, so for an embedded
 * message the content has already been written when the signatures are checked: do not
 * use it unless the result is acceptable.
 */
public final class CmsVerifier {

	private static final Set<ASN1ObjectIdentifier> WEAK_DIGESTS = Set.of(OIWObjectIdentifiers.idSHA1,
			PKCSObjectIdentifiers.md5, PKCSObjectIdentifiers.md2);

	private CmsVerifier() {
	}

	public static CmsVerifier create() {
		return new CmsVerifier();
	}

	/**
	 * Verifies a message with an embedded signature and writes its content to
	 * {@code content}. Neither stream is closed.
	 * @param request the signer certificates and policy
	 * @param message the signed message, DER or PEM
	 * @param content where the signed content is written
	 * @return one result per signature
	 * @throws CryptoException if the message cannot be read, has no embedded content, or
	 * is refused by the policy
	 */
	public VerifyResult verify(VerifyRequest request, InputStream message, OutputStream content) {
		try (InputStream in = ArmorInputStream.dearmorIfNeeded(message)) {
			CMSSignedDataParser parser = new CMSSignedDataParser(digestCalculatorProvider(), in);
			CMSTypedStream signedContent = parser.getSignedContent();
			if (signedContent == null) {
				throw new CryptoException("The message has no embedded content; it is a detached signature, "
						+ "so verify it against the data with verifyDetached");
			}
			try (InputStream stream = signedContent.getContentStream()) {
				stream.transferTo(content);
			}
			return check(request, parser);
		}
		catch (IOException | CMSException | OperatorCreationException ex) {
			throw new CryptoException("Cannot read the signed message", ex);
		}
	}

	/**
	 * Verifies a detached signature against the data it was made for. Neither stream is
	 * closed.
	 * @param request the signer certificates and policy
	 * @param data the data that was signed
	 * @param signature the detached signature, DER or PEM
	 * @return one result per signature
	 * @throws CryptoException if the signature cannot be read or is refused by the policy
	 */
	public VerifyResult verifyDetached(VerifyRequest request, InputStream data, InputStream signature) {
		try (InputStream signatureStream = ArmorInputStream.dearmorIfNeeded(signature)) {
			CMSSignedDataParser parser = new CMSSignedDataParser(digestCalculatorProvider(), new CMSTypedStream(data),
					signatureStream);
			parser.getSignedContent().drain();
			return check(request, parser);
		}
		catch (IOException | CMSException | OperatorCreationException ex) {
			throw new CryptoException("Cannot read the signature", ex);
		}
	}

	private static org.bouncycastle.operator.DigestCalculatorProvider digestCalculatorProvider()
			throws OperatorCreationException {
		return new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastle.provider()).build();
	}

	private static VerifyResult check(VerifyRequest request, CMSSignedDataParser parser)
			throws CMSException, IOException {
		List<SignatureResult> results = new ArrayList<>();
		for (SignerInformation signer : parser.getSignerInfos().getSigners()) {
			if (WEAK_DIGESTS.contains(signer.getDigestAlgorithmID().getAlgorithm()) && !request.legacyCompatibility()) {
				throw new CryptoException("The signature uses a weak digest (SHA-1 or older); "
						+ "enable legacy compatibility to accept it");
			}
			results.add(new SignatureResult(describe(signer.getSID()), status(request, signer), signingTime(signer)));
		}
		return new VerifyResult(results);
	}

	private static SignatureStatus status(VerifyRequest request, SignerInformation signer) throws CMSException {
		try {
			for (X509Certificate certificate : request.certificates()) {
				X509CertificateHolder holder = new JcaX509CertificateHolder(certificate);
				if (signer.getSID().match(holder)) {
					boolean valid = signer
						.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastle.provider())
							.build(certificate));
					return valid ? SignatureStatus.VALID : SignatureStatus.INVALID;
				}
			}
			return SignatureStatus.UNKNOWN_SIGNER;
		}
		catch (CMSSignerDigestMismatchException ex) {
			return SignatureStatus.INVALID;
		}
		catch (GeneralSecurityException | OperatorCreationException ex) {
			throw new CryptoException("Cannot check the signature", ex);
		}
	}

	private static Instant signingTime(SignerInformation signer) {
		if (signer.getSignedAttributes() == null) {
			return null;
		}
		Attribute attribute = signer.getSignedAttributes().get(CMSAttributes.signingTime);
		if (attribute == null) {
			return null;
		}
		return Time.getInstance(attribute.getAttrValues().getObjectAt(0)).getDate().toInstant();
	}

	private static String describe(SignerId id) {
		if (id.getSubjectKeyIdentifier() != null) {
			return "ski=" + Hex.toHexString(id.getSubjectKeyIdentifier());
		}
		return "issuer=" + id.getIssuer() + ", serial=" + id.getSerialNumber().toString(16);
	}

}
