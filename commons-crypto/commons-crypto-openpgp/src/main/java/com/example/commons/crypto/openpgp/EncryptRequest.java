package com.example.commons.crypto.openpgp;

import java.util.ArrayList;
import java.util.List;

/**
 * What to encrypt a message for and how to write it.
 */
public final class EncryptRequest {

	private final List<PgpPublicKeys> recipients;

	private final boolean armored;

	private final char[] passphrase;

	private EncryptRequest(Builder builder) {
		this.recipients = List.copyOf(builder.recipients);
		this.armored = builder.armored;
		this.passphrase = (builder.passphrase != null) ? builder.passphrase.clone() : null;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<PgpPublicKeys> recipients() {
		return this.recipients;
	}

	/**
	 * The passphrase the message can also be opened with, or null.
	 * @return a copy of the passphrase
	 */
	public char[] passphrase() {
		return (this.passphrase != null) ? this.passphrase.clone() : null;
	}

	/**
	 * Whether the output is ASCII armor instead of binary packets.
	 * @return true for ASCII armor
	 */
	public boolean armored() {
		return this.armored;
	}

	public static final class Builder {

		private final List<PgpPublicKeys> recipients = new ArrayList<>();

		private boolean armored;

		private char[] passphrase;

		private Builder() {
		}

		public Builder recipients(PgpPublicKeys... recipients) {
			this.recipients.addAll(List.of(recipients));
			return this;
		}

		/**
		 * Also lets anyone with the passphrase open the message. Without recipients the
		 * message is for the passphrase alone.
		 * @param passphrase the passphrase, which is copied
		 * @return this builder
		 */
		public Builder passphrase(char[] passphrase) {
			this.passphrase = passphrase.clone();
			return this;
		}

		public Builder armored(boolean armored) {
			this.armored = armored;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if there is neither a recipient nor a passphrase
		 */
		public EncryptRequest build() {
			if (this.recipients.isEmpty() && this.passphrase == null) {
				throw new IllegalStateException("At least one recipient or a passphrase is required");
			}
			return new EncryptRequest(this);
		}

	}

}
