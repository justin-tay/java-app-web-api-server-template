package com.example.commons.crypto.cms;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.SignatureMode;

/**
 * Who signs, and whether the signature is embedded with the data or detached from it.
 */
public final class SignRequest {

	private final List<Signer> signers;

	private final SignatureMode mode;

	private final boolean armored;

	private SignRequest(Builder builder) {
		this.signers = List.copyOf(builder.signers);
		this.mode = builder.mode;
		this.armored = builder.armored;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<Signer> signers() {
		return this.signers;
	}

	public SignatureMode mode() {
		return this.mode;
	}

	/**
	 * The same request with the output in DER, for a signature that is itself encrypted.
	 */
	SignRequest withoutArmor() {
		Builder copy = builder().mode(this.mode).armored(false);
		this.signers.forEach((signer) -> copy.signer(signer.key(), signer.certificate()));
		return copy.build();
	}

	/**
	 * Whether the output is PEM (base64 between BEGIN CMS and END CMS lines) instead of
	 * DER.
	 * @return true for PEM
	 */
	public boolean armored() {
		return this.armored;
	}

	/**
	 * A signing key and its certificate.
	 *
	 * @param key the private key, in software or on a token
	 * @param certificate the certificate of the key, which is also embedded in the
	 * message
	 */
	public record Signer(PrivateKeyHandle key, X509Certificate certificate) {
	}

	public static final class Builder {

		private final List<Signer> signers = new ArrayList<>();

		private SignatureMode mode = SignatureMode.EMBEDDED;

		private boolean armored;

		private Builder() {
		}

		/**
		 * Adds a signer. Each signer adds one signature.
		 * @param key the signing key
		 * @param certificate the certificate of the key
		 * @return this builder
		 */
		public Builder signer(PrivateKeyHandle key, X509Certificate certificate) {
			if (key == null || certificate == null) {
				throw new IllegalArgumentException("A key and its certificate are required");
			}
			this.signers.add(new Signer(key, certificate));
			return this;
		}

		public Builder mode(SignatureMode mode) {
			this.mode = mode;
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
