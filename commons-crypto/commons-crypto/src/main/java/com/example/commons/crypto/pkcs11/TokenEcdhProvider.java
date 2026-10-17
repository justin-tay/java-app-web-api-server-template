package com.example.commons.crypto.pkcs11;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyFactorySpi;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.agreement.kdf.ConcatenationKDFGenerator;
import org.bouncycastle.crypto.generators.KDF2BytesGenerator;
import org.bouncycastle.crypto.params.KDFParameters;
import org.bouncycastle.crypto.util.DigestFactory;
import org.bouncycastle.jcajce.spec.UserKeyingMaterialSpec;

/**
 * Lets Bouncy Castle use an elliptic curve key on a token for ECDH. Bouncy Castle asks
 * for a key agreement that includes the key derivation function by name (for example
 * {@code ECDHwithSHA256KDF} for CMS and {@code ECCDHwithSHA256CKDF} for OpenPGP), which
 * SunPKCS11 does not offer. This provider offers those names for keys that cannot be
 * extracted: the plain ECDH runs on the token and the key derivation runs in software.
 * Software keys are left to Bouncy Castle. It is registered once, when a token is first
 * opened.
 */
final class TokenEcdhProvider extends Provider {

	static final String NAME = "CommonsCryptoTokenEcdh";

	private static final long serialVersionUID = 1L;

	/**
	 * The providers of the tokens that are open, which do the plain ECDH for their own
	 * keys.
	 */
	private static final List<Provider> TOKENS = new CopyOnWriteArrayList<>();

	private TokenEcdhProvider() {
		super(NAME, "1", "ECDH on a token with the key derivation in software");
		// OpenPGP (RFC 6637): the concatenation KDF.
		register("ECCDHwithSHA256CKDF", false, "SHA-256");
		register("ECCDHwithSHA384CKDF", false, "SHA-384");
		register("ECCDHwithSHA512CKDF", false, "SHA-512");
		// CMS: the X9.63 KDF, with the ECC-CMS-SharedInfo that Bouncy Castle passes as
		// keying material.
		register("ECDHwithSHA1KDF", true, "SHA-1", "1.3.133.16.840.63.0.2");
		register("ECDHwithSHA224KDF", true, "SHA-224", "1.3.132.1.11.0");
		register("ECDHwithSHA256KDF", true, "SHA-256", "1.3.132.1.11.1");
		register("ECDHwithSHA384KDF", true, "SHA-384", "1.3.132.1.11.2");
		register("ECDHwithSHA512KDF", true, "SHA-512", "1.3.132.1.11.3");
		// Bouncy Castle looks the key factory up by these names, and the JDK's EC key
		// factory has
		// neither, so they are offered here and handed to the JDK's.
		putService(new Service(this, "KeyFactory", "ECDH", EcKeyFactory.class.getName(), List.of("1.2.840.10045.2.1"),
				null) {
			@Override
			public Object newInstance(Object constructorParameter) throws NoSuchAlgorithmException {
				return new EcKeyFactory();
			}
		});
	}

	static void tokenOpened(Provider token) {
		TOKENS.add(token);
	}

	static void tokenClosed(Provider token) {
		TOKENS.remove(token);
	}

	/**
	 * Registers the provider ahead of the others, once.
	 */
	static void register() {
		if (Security.getProvider(NAME) == null) {
			synchronized (TokenEcdhProvider.class) {
				if (Security.getProvider(NAME) == null) {
					Security.insertProviderAt(new TokenEcdhProvider(), 1);
				}
			}
		}
	}

	private void register(String name, boolean cms, String digest, String... aliases) {
		putService(new Service(this, "KeyAgreement", name, Agreement.class.getName(),
				(aliases.length == 0) ? null : List.of(aliases), null) {
			@Override
			public Object newInstance(Object constructorParameter) {
				return new Agreement(cms, digest);
			}

			@Override
			public boolean supportsParameter(Object key) {
				return key instanceof PrivateKey privateKey && "EC".equals(privateKey.getAlgorithm())
						&& privateKey.getEncoded() == null;
			}
		});
	}

	/** A key factory that is the JDK's EC key factory under other names. */
	static final class EcKeyFactory extends KeyFactorySpi {

		private final KeyFactory delegate;

		EcKeyFactory() throws NoSuchAlgorithmException {
			this.delegate = KeyFactory.getInstance("EC");
		}

		@Override
		protected PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException {
			return this.delegate.generatePublic(keySpec);
		}

		@Override
		protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException {
			return this.delegate.generatePrivate(keySpec);
		}

		@Override
		protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
			return this.delegate.getKeySpec(key, keySpec);
		}

		@Override
		protected Key engineTranslateKey(Key key) throws InvalidKeyException {
			return this.delegate.translateKey(key);
		}

	}

	/** The agreement: ECDH on the token, then the key derivation function. */
	static final class Agreement extends KeyAgreementSpi {

		private final boolean cms;

		private final String digest;

		private PrivateKey key;

		private PublicKey peer;

		private byte[] keyingMaterial;

		Agreement(boolean cms, String digest) {
			this.cms = cms;
			this.digest = digest;
		}

		@Override
		protected void engineInit(Key key, SecureRandom random) throws InvalidKeyException {
			if (!(key instanceof PrivateKey privateKey)) {
				throw new InvalidKeyException("not a private key");
			}
			this.key = privateKey;
			this.keyingMaterial = null;
		}

		@Override
		protected void engineInit(Key key, AlgorithmParameterSpec params, SecureRandom random)
				throws InvalidKeyException, InvalidAlgorithmParameterException {
			engineInit(key, random);
			if (params instanceof UserKeyingMaterialSpec spec) {
				this.keyingMaterial = spec.getUserKeyingMaterial();
			}
			else if (params != null) {
				throw new InvalidAlgorithmParameterException("unsupported parameters " + params);
			}
		}

		@Override
		protected Key engineDoPhase(Key key, boolean lastPhase) throws InvalidKeyException {
			if (!lastPhase) {
				throw new IllegalStateException("A single phase is expected");
			}
			this.peer = (PublicKey) key;
			return null;
		}

		private byte[] sharedSecret() {
			// The plain ECDH is done by the provider of the open token that holds the
			// key.
			for (Provider token : TOKENS) {
				try {
					KeyAgreement agreement = KeyAgreement.getInstance("ECDH", token);
					agreement.init(this.key);
					agreement.doPhase(this.peer, true);
					return agreement.generateSecret();
				}
				catch (InvalidKeyException ex) {
					// The key is on another token.
				}
				catch (GeneralSecurityException ex) {
					throw new IllegalStateException("The token could not do the key agreement", ex);
				}
			}
			throw new IllegalStateException("The key is not on a token that is open");
		}

		@Override
		protected byte[] engineGenerateSecret() {
			return sharedSecret();
		}

		@Override
		protected int engineGenerateSecret(byte[] sharedSecret, int offset) throws ShortBufferException {
			byte[] secret = sharedSecret();
			if (sharedSecret.length - offset < secret.length) {
				throw new ShortBufferException();
			}
			System.arraycopy(secret, 0, sharedSecret, offset, secret.length);
			return secret.length;
		}

		@Override
		protected SecretKey engineGenerateSecret(String algorithm) throws NoSuchAlgorithmException {
			int bits = switch (algorithm) {
				case "2.16.840.1.101.3.4.1.5", "AES128" -> 128;
				case "2.16.840.1.101.3.4.1.25", "AES192" -> 192;
				case "2.16.840.1.101.3.4.1.45", "AES256" -> 256;
				default -> throw new NoSuchAlgorithmException("Unsupported key algorithm " + algorithm);
			};
			byte[] secret = sharedSecret();
			byte[] derived = new byte[bits / 8];
			Digest digest = digest();
			if (this.cms) {
				KDF2BytesGenerator kdf = new KDF2BytesGenerator(digest);
				kdf.init(new KDFParameters(secret, this.keyingMaterial));
				kdf.generateBytes(derived, 0, derived.length);
			}
			else {
				ConcatenationKDFGenerator kdf = new ConcatenationKDFGenerator(digest);
				kdf.init(new KDFParameters(secret, this.keyingMaterial));
				kdf.generateBytes(derived, 0, derived.length);
			}
			Arrays.fill(secret, (byte) 0);
			return new SecretKeySpec(derived, "AES");
		}

		private Digest digest() {
			return switch (this.digest) {
				case "SHA-1" -> DigestFactory.createSHA1();
				case "SHA-224" -> DigestFactory.createSHA224();
				case "SHA-256" -> DigestFactory.createSHA256();
				case "SHA-384" -> DigestFactory.createSHA384();
				default -> DigestFactory.createSHA512();
			};
		}

	}

}
