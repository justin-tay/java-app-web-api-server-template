package com.example.commons.crypto.openpgp;

/**
 * The keys to decrypt a message with and the policy to apply.
 */
public final class DecryptRequest {

	private final PgpSecretKeys keys;

	private final char[] passphrase;

	private final boolean legacyCompatibility;

	private final long maxOutputBytes;

	private DecryptRequest(Builder builder) {
		this.keys = builder.keys;
		this.passphrase = (builder.passphrase != null) ? builder.passphrase.clone() : null;
		this.legacyCompatibility = builder.legacyCompatibility;
		this.maxOutputBytes = builder.maxOutputBytes;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The secret keys, or null if the request has only a passphrase.
	 * @return the keys
	 */
	public PgpSecretKeys keys() {
		return this.keys;
	}

	/**
	 * The passphrase for messages that were encrypted to a passphrase, or null.
	 * @return a copy of the passphrase
	 */
	public char[] passphrase() {
		return (this.passphrase != null) ? this.passphrase.clone() : null;
	}

	/**
	 * Whether messages that are not integrity protected or use weak ciphers are accepted.
	 * They are refused by default.
	 * @return true to accept them
	 */
	public boolean legacyCompatibility() {
		return this.legacyCompatibility;
	}

	/**
	 * The most bytes that may be written to the output stream, as a protection against a
	 * message that expands a great deal. Writing more fails with a
	 * {@code CryptoException}. There is no limit unless one is set.
	 * @return the limit, or {@code Long.MAX_VALUE} if there is none
	 */
	public long maxOutputBytes() {
		return this.maxOutputBytes;
	}

	public static final class Builder {

		private PgpSecretKeys keys;

		private char[] passphrase;

		private boolean legacyCompatibility;

		private long maxOutputBytes = Long.MAX_VALUE;

		private Builder() {
		}

		public Builder keys(PgpSecretKeys keys) {
			this.keys = keys;
			return this;
		}

		/**
		 * Lets the request open messages that were encrypted to a passphrase. It may be
		 * used with or without keys.
		 * @param passphrase the passphrase, which is copied
		 * @return this builder
		 */
		public Builder passphrase(char[] passphrase) {
			this.passphrase = passphrase.clone();
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
		 * @throws IllegalStateException if neither keys nor a passphrase were given
		 */
		public DecryptRequest build() {
			if (this.keys == null && this.passphrase == null) {
				throw new IllegalStateException("Secret keys or a passphrase are required");
			}
			return new DecryptRequest(this);
		}

	}

}
