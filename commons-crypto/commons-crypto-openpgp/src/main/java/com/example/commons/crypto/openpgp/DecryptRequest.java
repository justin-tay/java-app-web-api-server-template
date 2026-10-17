package com.example.commons.crypto.openpgp;

/**
 * The keys to decrypt a message with and the policy to apply.
 */
public final class DecryptRequest {

	private final PgpSecretKeys keys;

	private final boolean legacyCompatibility;

	private final long maxOutputBytes;

	private DecryptRequest(Builder builder) {
		this.keys = builder.keys;
		this.legacyCompatibility = builder.legacyCompatibility;
		this.maxOutputBytes = builder.maxOutputBytes;
	}

	public static Builder builder() {
		return new Builder();
	}

	public PgpSecretKeys keys() {
		return this.keys;
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

		private boolean legacyCompatibility;

		private long maxOutputBytes = Long.MAX_VALUE;

		private Builder() {
		}

		public Builder keys(PgpSecretKeys keys) {
			this.keys = keys;
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
		 * @throws IllegalStateException if no keys were given
		 */
		public DecryptRequest build() {
			if (this.keys == null) {
				throw new IllegalStateException("Secret keys are required");
			}
			return new DecryptRequest(this);
		}

	}

}
