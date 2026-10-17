package com.example.commons.crypto.openpgp;

import java.util.ArrayList;
import java.util.List;

/**
 * What to encrypt a message for and how to write it.
 */
public final class EncryptRequest {

	private final List<PgpPublicKeys> recipients;

	private final boolean armored;

	private EncryptRequest(Builder builder) {
		this.recipients = List.copyOf(builder.recipients);
		this.armored = builder.armored;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<PgpPublicKeys> recipients() {
		return this.recipients;
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

		private Builder() {
		}

		public Builder recipients(PgpPublicKeys... recipients) {
			this.recipients.addAll(List.of(recipients));
			return this;
		}

		public Builder armored(boolean armored) {
			this.armored = armored;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if there is no recipient
		 */
		public EncryptRequest build() {
			if (this.recipients.isEmpty()) {
				throw new IllegalStateException("At least one recipient is required");
			}
			return new EncryptRequest(this);
		}

	}

}
