package com.example.commons.crypto.openpgp;

/**
 * The keys to decrypt a message with and the policy to apply.
 */
public final class DecryptRequest {

	private final PgpSecretKeys keys;

	private final boolean legacyCompatibility;

	private DecryptRequest(Builder builder) {
		this.keys = builder.keys;
		this.legacyCompatibility = builder.legacyCompatibility;
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

	public static final class Builder {

		private PgpSecretKeys keys;

		private boolean legacyCompatibility;

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
