package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;
import com.example.commons.crypto.pkcs11.Pkcs11Config;
import com.example.commons.crypto.pkcs11.Pkcs11Token;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPAlgorithmParameters;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPKdfParameters;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenPGP with keys on a SoftHSM2 token, which has an RSA key under the alias k and an EC
 * P-256 key under the alias ec, with the PIN 1234. The OpenPGP public keys are built
 * around the token keys' public keys. See src/test/docker/pkcs11 in commons-crypto.
 * Skipped unless SOFTHSM2_LIBRARY is set.
 */
@EnabledIfEnvironmentVariable(named = "SOFTHSM2_LIBRARY", matches = ".+")
class PgpTokenIntegrationTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final int ENCRYPT = TokenKeyRings.ENCRYPT;

	private static final byte[] DATA = "Data for a key that never leaves the token. ".repeat(30)
		.getBytes(StandardCharsets.UTF_8);

	private static Pkcs11Token token;

	@BeforeAll
	static void openToken() {
		token = Pkcs11Token.open(Pkcs11Config.builder().library(Path.of(System.getenv("SOFTHSM2_LIBRARY"))).build(),
				(info) -> "1234".toCharArray());
	}

	@AfterAll
	static void closeToken() {
		token.close();
	}

	private static PgpPublicKeys ring(String alias, int flags) throws Exception {
		return TokenKeyRings.ring(token.certificate(alias).getPublicKey(), flags);
	}

	private static byte[] encrypt(PgpPublicKeys recipient) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.encrypt(EncryptRequest.builder().recipients(recipient).build(), new ByteArrayInputStream(DATA), out);
		return out.toByteArray();
	}

	private static byte[] decrypt(byte[] message, PgpSecretKeys keys) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PgpDecryptor.create()
			.decrypt(DecryptRequest.builder().keys(keys).build(), new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	private static void signsAndVerifies(String alias) throws Exception {
		PgpPublicKeys publicKeys = ring(alias, KeyFlags.SIGN_DATA);
		VerifyRequest request = VerifyRequest.builder().keys(publicKeys).build();
		for (SignatureMode mode : SignatureMode.values()) {
			ByteArrayOutputStream signed = new ByteArrayOutputStream();
			PgpSigner.create()
				.sign(SignRequest.builder()
					.signer(PgpSecretKeys.token(token.privateKey(alias), publicKeys))
					.mode(mode)
					.build(), new ByteArrayInputStream(DATA), signed);
			VerifyResult result = (mode == SignatureMode.EMBEDDED)
					? PgpVerifier.create()
						.verify(request, new ByteArrayInputStream(signed.toByteArray()), new ByteArrayOutputStream())
					: PgpVerifier.create()
						.verifyDetached(request, new ByteArrayInputStream(DATA),
								new ByteArrayInputStream(signed.toByteArray()));
			assertThat(result.allValid()).as("%s signature with %s", mode, alias).isTrue();
		}
		ByteArrayOutputStream cleartext = new ByteArrayOutputStream();
		PgpSigner.create()
			.sign(SignRequest.builder()
				.cleartext()
				.signer(PgpSecretKeys.token(token.privateKey(alias), publicKeys))
				.build(), new ByteArrayInputStream(DATA), cleartext);
		assertThat(PgpVerifier.create()
			.verify(request, new ByteArrayInputStream(cleartext.toByteArray()), new ByteArrayOutputStream())
			.allValid()).as("cleartext signature with %s", alias).isTrue();
	}

	@Test
	void decryptsOnTheTokenWithAnRsaKey() throws Exception {
		PgpPublicKeys publicKeys = ring("k", ENCRYPT);

		byte[] message = encrypt(publicKeys);

		assertThat(decrypt(message, PgpSecretKeys.token(token.privateKey("k"), publicKeys))).isEqualTo(DATA);
	}

	@Test
	void decryptsOnTheTokenWithAnEcdhKey() throws Exception {
		PgpPublicKeys publicKeys = ring("ec", ENCRYPT);

		byte[] message = encrypt(publicKeys);

		assertThat(decrypt(message, PgpSecretKeys.token(token.privateKey("ec"), publicKeys))).isEqualTo(DATA);
	}

	@Test
	void signsOnTheTokenWithAnRsaKey() throws Exception {
		signsAndVerifies("k");
	}

	@Test
	void signsOnTheTokenWithAnEcdsaKey() throws Exception {
		signsAndVerifies("ec");
	}

	@Test
	void publicKeysThatDoNotMatchTheTokenKeyAreRejected() throws Exception {
		PgpPublicKeys ecKeys = ring("ec", ENCRYPT);

		assertThatThrownBy(() -> PgpSecretKeys.token(token.privateKey("k"), ecKeys)).isInstanceOf(CryptoException.class)
			.hasMessageContaining("matches");
	}

	@Test
	void aTokenKeyWhoseFlagsDoNotAllowSigningDoesNotSign() throws Exception {
		PgpPublicKeys encryptionOnly = ring("k", ENCRYPT);
		PgpSecretKeys keys = PgpSecretKeys.token(token.privateKey("k"), encryptionOnly);

		assertThatThrownBy(() -> PgpSigner.create()
			.sign(SignRequest.builder().signer(keys).build(), new ByteArrayInputStream(DATA),
					new ByteArrayOutputStream()))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("not allowed to sign");
	}

}
