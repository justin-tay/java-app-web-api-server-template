package com.example.commons.crypto.pkcs11;

import java.nio.file.Path;

/**
 * Where a PKCS#11 token is. The library builds the provider configuration from this,
 * including the attributes elliptic curve key agreement needs, so no configuration file
 * is required.
 */
public final class Pkcs11Config {

	private final Path library;

	private final Integer slotListIndex;

	private final Long slot;

	private Pkcs11Config(Builder builder) {
		this.library = builder.library;
		this.slotListIndex = builder.slotListIndex;
		this.slot = builder.slot;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Path library() {
		return this.library;
	}

	/**
	 * The text SunPKCS11 is configured with.
	 * @param name the provider name, which must be unique among providers
	 * @return the configuration
	 */
	String providerConfiguration(String name) {
		StringBuilder text = new StringBuilder("--");
		text.append("name=").append(name).append('\n');
		text.append("library=\"").append(this.library.toAbsolutePath().toString().replace('\\', '/')).append("\"\n");
		if (this.slot != null) {
			text.append("slot=").append(this.slot).append('\n');
		}
		else if (this.slotListIndex != null) {
			text.append("slotListIndex=").append(this.slotListIndex).append('\n');
		}
		// A key agreement derives a secret key on the token and then reads it, which
		// needs it
		// to be readable. Only the shared secret becomes readable, never a private key.
		text.append("attributes(*,CKO_SECRET_KEY,CKK_GENERIC_SECRET) = {\n");
		text.append(" CKA_SENSITIVE = false\n");
		text.append(" CKA_EXTRACTABLE = true\n");
		text.append("}\n");
		return text.toString();
	}

	public static final class Builder {

		private Path library;

		private Integer slotListIndex;

		private Long slot;

		private Builder() {
		}

		/**
		 * The PKCS#11 module, for example libsofthsm2.so or the vendor's library.
		 * @param library the path of the module
		 * @return this builder
		 */
		public Builder library(Path library) {
			this.library = library;
			return this;
		}

		/**
		 * The position of the token among the slots that have a token, starting at 0.
		 * This is the default, with 0.
		 * @param slotListIndex the position
		 * @return this builder
		 */
		public Builder slotListIndex(int slotListIndex) {
			if (slotListIndex < 0) {
				throw new IllegalArgumentException("slotListIndex must not be negative");
			}
			this.slotListIndex = slotListIndex;
			return this;
		}

		/**
		 * The slot ID, which takes the place of the slot list index.
		 * @param slot the slot ID
		 * @return this builder
		 */
		public Builder slot(long slot) {
			this.slot = slot;
			return this;
		}

		/**
		 * Builds the configuration.
		 * @return the configuration
		 * @throws IllegalStateException if no library was given
		 */
		public Pkcs11Config build() {
			if (this.library == null) {
				throw new IllegalStateException("A PKCS#11 library is required");
			}
			return new Pkcs11Config(this);
		}

	}

}
