package com.example.commons.crypto.openpgp;

import java.util.ArrayList;
import java.util.List;

/**
 * The public keys to check signatures against and the policy to apply. The library checks
 * signatures only: it does not decide whether a signer is trusted.
 */
public final class VerifyRequest {

	private final List<PgpPublicKeys> keys;

	private final boolean legacyCompatibility;

	private VerifyRequest(Builder builder) {
		this.keys = List.copyOf(builder.keys);
		this.legacyCompatibility = builder.legacyCompatibility;
	}

	public static Builder builder() {
		return new Builder();
	}

	List<PgpPublicKeys> keys() {
		return this.keys;
	}

	/**
	 * Whether signatures that use weak hashes such as SHA-1 are accepted. They are
	 * refused by default.
	 * @return true to accept them
	 */
	public boolean legacyCompatibility() {
		return this.legacyCompatibility;
	}

	/** The key with the given ID in any of the supplied keys, or null. */
	org.bouncycastle.openpgp.PGPPublicKey find(long keyId) {
		for (PgpPublicKeys candidate : this.keys) {
			org.bouncycastle.openpgp.PGPPublicKey key = candidate.publicKey(keyId);
			if (key != null) {
				return key;
			}
		}
		return null;
	}

	public static final class Builder {

		private final List<PgpPublicKeys> keys = new ArrayList<>();

		private boolean legacyCompatibility;

		private Builder() {
		}

		public Builder keys(PgpPublicKeys... keys) {
			this.keys.addAll(List.of(keys));
			return this;
		}

		public Builder legacyCompatibility(boolean legacyCompatibility) {
			this.legacyCompatibility = legacyCompatibility;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if there is no key
		 */
		public VerifyRequest build() {
			if (this.keys.isEmpty()) {
				throw new IllegalStateException("At least one public key is required");
			}
			return new VerifyRequest(this);
		}

	}

}
