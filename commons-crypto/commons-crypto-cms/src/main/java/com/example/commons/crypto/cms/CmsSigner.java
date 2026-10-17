package com.example.commons.crypto.cms;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.List;

import com.example.commons.crypto.TokenFailures;
import com.example.commons.crypto.Streams;
import com.example.commons.crypto.BouncyCastle;
import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureMode;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataStreamGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Signs data as CMS {@code SignedData}, embedded or detached, with one signature per
 * signer. RSA keys sign with SHA-256 and PKCS#1 v1.5; EC keys sign with ECDSA and
 * SHA-256, SHA-384 or SHA-512 by curve size. The signers' certificates are included in
 * the message. The data is streamed.
 */
public final class CmsSigner {

	private CmsSigner() {
	}

	public static CmsSigner create() {
		return new CmsSigner();
	}

	/**
	 * Signs everything read from {@code in}. For an embedded signature {@code out}
	 * receives the data and the signature; for a detached signature it receives the
	 * signature only. Neither stream is closed.
	 * @param request the signers, mode and output format
	 * @param in the data to sign
	 * @param out where the signed message or the detached signature is written
	 * @throws CryptoException if a key type is not supported or signing fails
	 */
	public void sign(SignRequest request, InputStream in, OutputStream out) {
		try {
			CMSSignedDataStreamGenerator generator = new CMSSignedDataStreamGenerator();
			List<X509Certificate> certificates = new ArrayList<>();
			for (SignRequest.Signer signer : request.signers()) {
				generator.addSignerInfoGenerator(signerInfoGenerator(signer));
				certificates.add(signer.certificate());
			}
			generator.addCertificates(new JcaCertStore(certificates));
			OutputStream target = request.armored() ? new ArmorOutputStream(out) : Streams.unclosable(out);
			try (OutputStream signed = generator.open(target, request.mode() == SignatureMode.EMBEDDED)) {
				in.transferTo(signed);
			}
			target.close();
		}
		catch (IOException | CMSException | GeneralSecurityException | OperatorCreationException ex) {
			throw TokenFailures.translate(ex, "Cannot sign the data");
		}
		catch (RuntimeException ex) {
			throw TokenFailures.translate(ex);
		}
	}

	private static SignerInfoGenerator signerInfoGenerator(SignRequest.Signer signer)
			throws OperatorCreationException, GeneralSecurityException {
		Provider provider = signer.key().provider().orElse(BouncyCastle.provider());
		String algorithm = signatureAlgorithm(signer.certificate().getPublicKey());
		return new JcaSignerInfoGeneratorBuilder(
				new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastle.provider()).build())
			.build(new JcaContentSignerBuilder(algorithm).setProvider(provider).build(signer.key().key()),
					signer.certificate());
	}

	private static String signatureAlgorithm(PublicKey publicKey) {
		if (publicKey instanceof ECPublicKey ecKey) {
			int bits = ecKey.getParams().getOrder().bitLength();
			return (bits <= 256) ? "SHA256withECDSA" : (bits <= 384) ? "SHA384withECDSA" : "SHA512withECDSA";
		}
		if ("RSA".equals(publicKey.getAlgorithm())) {
			return "SHA256withRSA";
		}
		throw new CryptoException("Unsupported signing key type " + publicKey.getAlgorithm());
	}

}
