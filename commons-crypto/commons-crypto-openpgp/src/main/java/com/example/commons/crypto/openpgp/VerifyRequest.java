package com.example.commons.crypto.openpgp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.commons.crypto.SignatureStatus;

/**
 * The public keys to check signatures against and the policy to apply. The library checks
 * signatures only: it does not decide whether a signer is trusted.
 */
public final class VerifyRequest {

	private final List<PgpPublicKeys> keys;

	private final boolean legacyCompatibility;

	private final boolean checkKeyValidity;

	private final long maxOutputBytes;

	private VerifyRequest(Builder builder) {
		this.keys = List.copyOf(builder.keys);
		this.legacyCompatibility = builder.legacyCompatibility;
		this.checkKeyValidity = builder.checkKeyValidity;
		this.maxOutputBytes = builder.maxOutputBytes;
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

	/**
	 * Whether a signature is reported as {@code KEY_REVOKED} or {@code KEY_EXPIRED} when
	 * the signing key was revoked, or had expired when it signed. Off by default.
	 * @return true to check
	 */
	public boolean checkKeyValidity() {
		return this.checkKeyValidity;
	}

	/**
	 * Why the key with the given ID is not good for a signature made at the time, or
	 * null.
	 */
	SignatureStatus keyProblem(long keyId, Instant time) {
		for (PgpPublicKeys candidate : this.keys) {
			if (candidate.publicKey(keyId) != null) {
				return candidate.keyProblem(keyId, time);
			}
		}
		return null;
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

		private final List<PgpPublicKeys> keys = new ArrayList<>();

		private boolean legacyCompatibility;

		private boolean checkKeyValidity;

		private long maxOutputBytes = Long.MAX_VALUE;

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
		 * Reports a signature as revoked or expired when the signing key was revoked, or
		 * had expired at the time it signed (the signature's own time, or now if it has
		 * none). Revocation is whatever the supplied key blocks contain; the library does
		 * not fetch updates.
		 * @param checkKeyValidity true to check
		 * @return this builder
		 */
		public Builder checkKeyValidity(boolean checkKeyValidity) {
			this.checkKeyValidity = checkKeyValidity;
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
