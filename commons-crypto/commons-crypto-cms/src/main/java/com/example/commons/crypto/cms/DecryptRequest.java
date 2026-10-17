package com.example.commons.crypto.cms;

import java.security.cert.X509Certificate;

import com.example.commons.crypto.PrivateKeyHandle;

/**
 * The key to decrypt a message with and the policy to apply.
 */
public final class DecryptRequest {

	private final PrivateKeyHandle key;

	private final X509Certificate certificate;

	private final char[] password;

	private final boolean legacyCompatibility;

	private final long maxOutputBytes;

	private DecryptRequest(Builder builder) {
		this.key = builder.key;
		this.certificate = builder.certificate;
		this.password = (builder.password != null) ? builder.password.clone() : null;
		this.legacyCompatibility = builder.legacyCompatibility;
		this.maxOutputBytes = builder.maxOutputBytes;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The key, or null if the request has only a password.
	 * @return the key
	 */
	public PrivateKeyHandle key() {
		return this.key;
	}

	/**
	 * The password for messages that were encrypted to a password, or null.
	 * @return a copy of the password
	 */
	public char[] password() {
		return (this.password != null) ? this.password.clone() : null;
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

	/**
	 * The most bytes that may be written to the output stream, as a protection against a
	 * message that is much larger than expected. Writing more fails with a
	 * {@code CryptoException}, and nothing past the limit is written. There is no limit
	 * unless one is set.
	 * @return the limit, or {@code Long.MAX_VALUE} if there is none
	 */
	public long maxOutputBytes() {
		return this.maxOutputBytes;
	}

	public static final class Builder {

		private PrivateKeyHandle key;

		private X509Certificate certificate;

		private char[] password;

		private boolean legacyCompatibility;

		private long maxOutputBytes = Long.MAX_VALUE;

		private Builder() {
		}

		public Builder key(PrivateKeyHandle key, X509Certificate certificate) {
			this.key = key;
			this.certificate = certificate;
			return this;
		}

		/**
		 * Lets the request open messages that were encrypted to a password. It may be
		 * used with or without a key.
		 * @param password the password, which is copied
		 * @return this builder
		 */
		public Builder password(char[] password) {
			this.password = password.clone();
			return this;
		}

		public Builder legacyCompatibility(boolean legacyCompatibility) {
			this.legacyCompatibility = legacyCompatibility;
			return this;
		}

		/**
		 * Limits how much may be written to the output stream.
		 * @param maxOutputBytes the most bytes to write, which must be positive
		 * @return this builder
		 */
		public Builder maxOutputBytes(long maxOutputBytes) {
			if (maxOutputBytes <= 0) {
				throw new IllegalArgumentException("maxOutputBytes must be positive");
			}
			this.maxOutputBytes = maxOutputBytes;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if neither a key with its certificate nor a
		 * password was given
		 */
		public DecryptRequest build() {
			if (this.password == null && (this.key == null || this.certificate == null)) {
				throw new IllegalStateException("A key and its certificate, or a password, are required");
			}
			return new DecryptRequest(this);
		}

	}

}
