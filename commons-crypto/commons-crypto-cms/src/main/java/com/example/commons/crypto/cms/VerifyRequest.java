package com.example.commons.crypto.cms;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * The signer certificates to check signatures against and the policy to apply. The
 * library checks signatures only: it does not build a certificate chain or check
 * revocation, and it does not decide whether a signer is trusted.
 */
public final class VerifyRequest {

	private final List<X509Certificate> certificates;

	private final boolean legacyCompatibility;

	private VerifyRequest(Builder builder) {
		this.certificates = List.copyOf(builder.certificates);
		this.legacyCompatibility = builder.legacyCompatibility;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The certificates of the signers the caller knows. A signature by anyone else is
	 * reported as an unknown signer.
	 * @return the certificates
	 */
	public List<X509Certificate> certificates() {
		return this.certificates;
	}

	/**
	 * Whether signatures that use weak digests such as SHA-1 are accepted. They are
	 * refused by default.
	 * @return true to accept them
	 */
	public boolean legacyCompatibility() {
		return this.legacyCompatibility;
	}

	public static final class Builder {

		private final List<X509Certificate> certificates = new ArrayList<>();

		private boolean legacyCompatibility;

		private Builder() {
		}

		public Builder certificates(X509Certificate... certificates) {
			this.certificates.addAll(List.of(certificates));
			return this;
		}

		public Builder legacyCompatibility(boolean legacyCompatibility) {
			this.legacyCompatibility = legacyCompatibility;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if there is no certificate
		 */
		public VerifyRequest build() {
			if (this.certificates.isEmpty()) {
				throw new IllegalStateException("At least one signer certificate is required");
			}
			return new VerifyRequest(this);
		}

	}

}
