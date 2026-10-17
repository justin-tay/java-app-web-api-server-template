package com.example.commons.crypto.cms;

import java.security.cert.X509Certificate;

import com.example.commons.crypto.PrivateKeyHandle;

/**
 * The key to decrypt a message with and the policy to apply.
 */
public final class DecryptRequest {

	private final PrivateKeyHandle key;

	private final X509Certificate certificate;

	private final boolean legacyCompatibility;

	private DecryptRequest(Builder builder) {
		this.key = builder.key;
		this.certificate = builder.certificate;
		this.legacyCompatibility = builder.legacyCompatibility;
	}

	public static Builder builder() {
		return new Builder();
	}

	public PrivateKeyHandle key() {
		return this.key;
	}

	/**
	 * The certificate of {@link #key()}, which identifies the recipient in the message.
	 * @return the certificate
	 */
	public X509Certificate certificate() {
		return this.certificate;
	}

	/**
	 * Whether messages that are not integrity protected or use PKCS#1 v1.5 key transport
	 * are accepted. They are refused by default.
	 * @return true to accept them
	 */
	public boolean legacyCompatibility() {
		return this.legacyCompatibility;
	}

	public static final class Builder {

		private PrivateKeyHandle key;

		private X509Certificate certificate;

		private boolean legacyCompatibility;

		private Builder() {
		}

		public Builder key(PrivateKeyHandle key, X509Certificate certificate) {
			this.key = key;
			this.certificate = certificate;
			return this;
		}

		public Builder legacyCompatibility(boolean legacyCompatibility) {
			this.legacyCompatibility = legacyCompatibility;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if no key was given
		 */
		public DecryptRequest build() {
			if (this.key == null || this.certificate == null) {
				throw new IllegalStateException("A key and its certificate are required");
			}
			return new DecryptRequest(this);
		}

	}

}
