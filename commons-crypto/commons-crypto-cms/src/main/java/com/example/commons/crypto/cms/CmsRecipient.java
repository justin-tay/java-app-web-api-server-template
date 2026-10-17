package com.example.commons.crypto.cms;

import java.security.cert.X509Certificate;

/**
 * Someone a message is encrypted for, named the way CMS names them.
 *
 * @param certificate the recipient's X.509 certificate, with an RSA or an EC public key
 */
public record CmsRecipient(X509Certificate certificate) {

	public CmsRecipient {
		if (certificate == null) {
			throw new IllegalArgumentException("certificate is required");
		}
	}

	/**
	 * A recipient identified by an X.509 certificate.
	 * @param certificate the recipient's certificate
	 * @return the recipient
	 */
	public static CmsRecipient certificate(X509Certificate certificate) {
		return new CmsRecipient(certificate);
	}

}
