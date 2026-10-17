package com.example.commons.crypto.cms;

import java.util.ArrayList;
import java.util.List;

/**
 * What to encrypt a message for and how to write it.
 */
public final class EncryptRequest {

	static final int DEFAULT_PASSWORD_ITERATIONS = 600_000;

	private final List<CmsRecipient> recipients;

	private final boolean armored;

	private final char[] password;

	private final int passwordIterations;

	private EncryptRequest(Builder builder) {
		this.recipients = List.copyOf(builder.recipients);
		this.armored = builder.armored;
		this.password = (builder.password != null) ? builder.password.clone() : null;
		this.passwordIterations = builder.passwordIterations;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<CmsRecipient> recipients() {
		return this.recipients;
	}

	/**
	 * The password the message can also be opened with, or null.
	 * @return a copy of the password
	 */
	public char[] password() {
		return (this.password != null) ? this.password.clone() : null;
	}

	/**
	 * The PBKDF2 iterations that turn the password into a key.
	 * @return the iteration count
	 */
	public int passwordIterations() {
		return this.passwordIterations;
	}

	/**
	 * Whether the output is PEM (base64 between BEGIN CMS and END CMS lines) instead of
	 * DER.
	 * @return true for PEM
	 */
	public boolean armored() {
		return this.armored;
	}

	public static final class Builder {

		private final List<CmsRecipient> recipients = new ArrayList<>();

		private boolean armored;

		private char[] password;

		private int passwordIterations = DEFAULT_PASSWORD_ITERATIONS;

		private Builder() {
		}

		public Builder recipients(CmsRecipient... recipients) {
			this.recipients.addAll(List.of(recipients));
			return this;
		}

		/**
		 * Also lets anyone with the password open the message (a CMS password recipient,
		 * with PBKDF2 and the AES-256 CBC key wrap of RFC 3211). Without certificates the
		 * message is for the password alone.
		 * @param password the password, which is copied
		 * @return this builder
		 */
		public Builder password(char[] password) {
			this.password = password.clone();
			return this;
		}

		/**
		 * Sets the PBKDF2 iterations for the password, 600000 unless set. A reader
		 * refuses a message with more than 5 million.
		 * @param passwordIterations the iteration count, which must be positive
		 * @return this builder
		 */
		public Builder passwordIterations(int passwordIterations) {
			if (passwordIterations <= 0) {
				throw new IllegalArgumentException("passwordIterations must be positive");
			}
			this.passwordIterations = passwordIterations;
			return this;
		}

		public Builder armored(boolean armored) {
			this.armored = armored;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if there is neither a recipient nor a password
		 */
		public EncryptRequest build() {
			if (this.recipients.isEmpty() && this.password == null) {
				throw new IllegalStateException("At least one recipient or a password is required");
			}
			return new EncryptRequest(this);
		}

	}

}
