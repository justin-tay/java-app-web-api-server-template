package com.example.commons.crypto.testsupport;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureSpi;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.CipherSpi;
import javax.crypto.NoSuchPaddingException;

/**
 * A provider that does everything another provider does, except that one operation fails the way a
 * token that was removed does: with a provider exception whose cause names CKR_DEVICE_REMOVED. For
 * testing what the library does when a token goes away.
 */
public final class FailingProvider extends Provider {

	/** The failure a removed token causes. */
	public static final String REMOVED = "CKR_DEVICE_REMOVED";

	private static final long serialVersionUID = 1L;

	private final transient Provider delegate;

	private final String type;

	private final String algorithmPrefix;

	private FailingProvider(Provider delegate, String type, String algorithmPrefix) {
		super("failing", "1", "fails " + type + " " + algorithmPrefix);
		this.delegate = delegate;
		this.type = type;
		this.algorithmPrefix = algorithmPrefix.toLowerCase(java.util.Locale.ROOT);
	}

	/** Signatures with this algorithm fail when they sign. */
	public static FailingProvider failingSignature(Provider delegate, String algorithm) {
		return new FailingProvider(delegate, "Signature", algorithm);
	}

	/** Ciphers whose name starts with this fail when they decrypt or unwrap. */
	public static FailingProvider failingCipher(Provider delegate, String algorithmPrefix) {
		return new FailingProvider(delegate, "Cipher", algorithmPrefix);
	}

	@Override
	public synchronized Service getService(String requestedType, String algorithm) {
		if (this.type.equals(requestedType)
				&& algorithm.toLowerCase(java.util.Locale.ROOT).startsWith(this.algorithmPrefix)) {
			return new Service(this, requestedType, algorithm, "failing", null, null) {
				@Override
				public Object newInstance(Object constructorParameter) {
					return "Signature".equals(requestedType) ? new FailingSignature() : new FailingCipher();
				}
			};
		}
		return this.delegate.getService(requestedType, algorithm);
	}

	private static ProviderException removed() {
		return new ProviderException("operation failed", new RuntimeException(REMOVED));
	}

	private static final class FailingSignature extends SignatureSpi {

		@Override
		protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
		}

		@Override
		protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
		}

		@Override
		protected void engineUpdate(byte b) {
		}

		@Override
		protected void engineUpdate(byte[] b, int off, int len) {
		}

		@Override
		protected byte[] engineSign() {
			throw removed();
		}

		@Override
		protected boolean engineVerify(byte[] sigBytes) {
			throw removed();
		}

		@Override
		@SuppressWarnings("deprecation")
		protected void engineSetParameter(String param, Object value) {
		}

		@Override
		@SuppressWarnings("deprecation")
		protected Object engineGetParameter(String param) {
			return null;
		}

	}

	private static final class FailingCipher extends CipherSpi {

		@Override
		protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
		}

		@Override
		protected void engineSetPadding(String padding) throws NoSuchPaddingException {
		}

		@Override
		protected int engineGetBlockSize() {
			return 0;
		}

		@Override
		protected int engineGetOutputSize(int inputLen) {
			return inputLen;
		}

		@Override
		protected byte[] engineGetIV() {
			return null;
		}

		@Override
		protected AlgorithmParameters engineGetParameters() {
			return null;
		}

		@Override
		protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
		}

		@Override
		protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
				throws InvalidKeyException, InvalidAlgorithmParameterException {
		}

		@Override
		protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
				throws InvalidKeyException, InvalidAlgorithmParameterException {
		}

		@Override
		protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
			return new byte[0];
		}

		@Override
		protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) {
			return 0;
		}

		@Override
		protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) {
			throw removed();
		}

		@Override
		protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) {
			throw removed();
		}

		@Override
		protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType) {
			throw removed();
		}

	}

}
