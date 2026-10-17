package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;
import com.example.commons.crypto.pkcs11.Pkcs11Config;
import com.example.commons.crypto.pkcs11.Pkcs11PrivateKeyHandle;
import com.example.commons.crypto.pkcs11.Pkcs11Token;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputAEADEncryptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CMS with keys on a PKCS#11 token, which has an RSA key under the alias k and an EC
 * P-256 key under the alias ec, with the PIN 1234. See src/test/docker/pkcs11 in
 * commons-crypto. Skipped unless PKCS11_LIBRARY is set.
 */
@EnabledIfEnvironmentVariable(named = "PKCS11_LIBRARY", matches = ".+")
class CmsTokenIntegrationTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "Data for a key that never leaves the token. ".repeat(30)
		.getBytes(StandardCharsets.UTF_8);

	private static Pkcs11Token token;

	@BeforeAll
	static void openToken() {
		token = Pkcs11Token.open(TestToken.config(), (info) -> TestToken.pin());
	}

	@AfterAll
	static void closeToken() {
		token.close();
	}

	private static byte[] encrypt(X509Certificate certificate) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CmsEncryptor.create()
			.encrypt(EncryptRequest.builder().recipients(CmsRecipient.certificate(certificate)).build(),
					new ByteArrayInputStream(DATA), out);
		return out.toByteArray();
	}

	private static byte[] decrypt(byte[] message, Pkcs11PrivateKeyHandle key, X509Certificate certificate) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CmsDecryptor.create()
			.decrypt(DecryptRequest.builder().key(key, certificate).build(), new ByteArrayInputStream(message), out);
		return out.toByteArray();
	}

	private static void signsAndVerifies(String alias) {
		X509Certificate certificate = token.certificate(alias);
		for (SignatureMode mode : SignatureMode.values()) {
			ByteArrayOutputStream signed = new ByteArrayOutputStream();
			CmsSigner.create()
				.sign(SignRequest.builder().signer(token.privateKey(alias), certificate).mode(mode).build(),
						new ByteArrayInputStream(DATA), signed);

			VerifyRequest request = VerifyRequest.builder().certificates(certificate).build();
			VerifyResult result = (mode == SignatureMode.EMBEDDED)
					? CmsVerifier.create()
						.verify(request, new ByteArrayInputStream(signed.toByteArray()), new ByteArrayOutputStream())
					: CmsVerifier.create()
						.verifyDetached(request, new ByteArrayInputStream(DATA),
								new ByteArrayInputStream(signed.toByteArray()));
			assertThat(result.allValid()).as("%s signature with %s", mode, alias).isTrue();
		}
	}

	@Test
	void signsOnTheTokenWithAnRsaKey() {
		signsAndVerifies(TestToken.RSA_ALIAS);
	}

	@Test
	void signsOnTheTokenWithAnEcKey() {
		signsAndVerifies(TestToken.EC_ALIAS);
	}

	@Test
	void decryptsOnTheTokenForAnEcRecipient() {
		X509Certificate certificate = token.certificate(TestToken.EC_ALIAS);

		byte[] message = encrypt(certificate);

		assertThat(decrypt(message, token.privateKey(TestToken.EC_ALIAS), certificate)).isEqualTo(DATA);
	}

	@Test
	void decryptsOnTheTokenForSeveralEcRecipientsInOneMessage() {
		X509Certificate certificate = token.certificate(TestToken.EC_ALIAS);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CmsEncryptor.create()
			.encrypt(EncryptRequest.builder()
				.recipients(CmsRecipient.certificate(certificate),
						CmsRecipient.certificate(token.certificate(TestToken.RSA_ALIAS)))
				.build(), new ByteArrayInputStream(DATA), out);

		assertThat(decrypt(out.toByteArray(), token.privateKey(TestToken.EC_ALIAS), certificate)).isEqualTo(DATA);
	}

	@Test
	void anRsaOaepRecipientFailsClearlyBecauseSunPkcs11HasNoOaep() {
		X509Certificate certificate = token.certificate(TestToken.RSA_ALIAS);
		byte[] message = encrypt(certificate);

		assertThatThrownBy(() -> decrypt(message, token.privateKey(TestToken.RSA_ALIAS), certificate))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("OAEP");
	}

	@Test
	void anRsaPkcs1v15RecipientNeedsTheOptInOnTheTokenKey() throws Exception {
		X509Certificate certificate = token.certificate(TestToken.RSA_ALIAS);
		CMSAuthEnvelopedDataGenerator generator = new CMSAuthEnvelopedDataGenerator();
		generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(certificate).setProvider(BC));
		byte[] message = generator
			.generate(new CMSProcessableByteArray(DATA),
					(OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM).setProvider(BC)
						.build())
			.getEncoded();

		assertThatThrownBy(() -> decrypt(message, token.privateKey(TestToken.RSA_ALIAS), certificate))
			.isInstanceOf(CryptoException.class)
			.hasMessageContaining("PKCS#1 v1.5");
		assertThat(decrypt(message, token.privateKey(TestToken.RSA_ALIAS).allowPkcs1v15KeyTransport(), certificate))
			.isEqualTo(DATA);
	}

}
