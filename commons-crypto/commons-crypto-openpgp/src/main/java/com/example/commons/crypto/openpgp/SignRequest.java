package com.example.commons.crypto.openpgp;

import java.util.ArrayList;
import java.util.List;

import com.example.commons.crypto.SignatureMode;

/**
 * Who signs, and whether the signature is embedded with the data, detached from it, or a
 * cleartext signature.
 */
public final class SignRequest {

	/** Where the signature goes. */
	enum Form {

		EMBEDDED, DETACHED, CLEARTEXT

	}

	private final List<PgpSecretKeys> signers;

	private final Form form;

	private final boolean armored;

	private SignRequest(Builder builder) {
		this.signers = List.copyOf(builder.signers);
		this.form = builder.form;
		this.armored = builder.armored;
	}

	public static Builder builder() {
		return new Builder();
	}

	List<PgpSecretKeys> signers() {
		return this.signers;
	}

	Form form() {
		return this.form;
	}

	/**
	 * Whether the output is ASCII armor instead of binary packets. A cleartext signature
	 * is always armored.
	 * @return true for ASCII armor
	 */
	boolean armored() {
		return this.armored || this.form == Form.CLEARTEXT;
	}

	public static final class Builder {

		private final List<PgpSecretKeys> signers = new ArrayList<>();

		private Form form = Form.EMBEDDED;

		private boolean armored;

		private Builder() {
		}

		/**
		 * Adds a signer. Each signer adds one signature, made with the first key in the
		 * keys that may sign.
		 * @param keys the signer's secret keys
		 * @return this builder
		 */
		public Builder signer(PgpSecretKeys keys) {
			if (keys == null) {
				throw new IllegalArgumentException("keys are required");
			}
			this.signers.add(keys);
			return this;
		}

		public Builder mode(SignatureMode mode) {
			this.form = (mode == SignatureMode.DETACHED) ? Form.DETACHED : Form.EMBEDDED;
			return this;
		}

		/**
		 * Signs the data as text and leaves it readable, between a BEGIN PGP SIGNED
		 * MESSAGE line and the signature. The data is read into memory, is taken as UTF-8
		 * text, and is signed with its line endings as LF and trailing spaces and tabs
		 * removed.
		 * @return this builder
		 */
		public Builder cleartext() {
			this.form = Form.CLEARTEXT;
			return this;
		}

		public Builder armored(boolean armored) {
			this.armored = armored;
			return this;
		}

		/**
		 * Builds the request.
		 * @return the request
		 * @throws IllegalStateException if there is no signer
		 */
		public SignRequest build() {
			if (this.signers.isEmpty()) {
				throw new IllegalStateException("At least one signer is required");
			}
			return new SignRequest(this);
		}

	}

}
