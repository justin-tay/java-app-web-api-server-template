package com.example.commons.crypto.cms;

import java.util.ArrayList;
import java.util.List;

/**
 * What to encrypt a message for and how to write it.
 */
public final class EncryptRequest {

	private final List<CmsRecipient> recipients;

	private final boolean armored;

	private EncryptRequest(Builder builder) {
		this.recipients = List.copyOf(builder.recipients);
		this.armored = builder.armored;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<CmsRecipient> recipients() {
		return this.recipients;
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

		private Builder() {
		}

		public Builder recipients(CmsRecipient... recipients) {
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
