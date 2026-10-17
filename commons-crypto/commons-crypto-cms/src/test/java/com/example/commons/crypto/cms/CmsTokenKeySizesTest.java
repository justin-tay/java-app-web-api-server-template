package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.List;

import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;
import com.example.commons.crypto.pkcs11.Pkcs11Token;
import com.example.commons.crypto.testsupport.TestToken;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputAEADEncryptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CMS with the other keys on the token (bigger RSA keys, other curves), named by
 * PKCS11_EXTRA_ALIASES. See src/test/docker/pkcs11 in commons-crypto.
 */
@EnabledIfEnvironmentVariable(named = "PKCS11_EXTRA_ALIASES", matches = ".+")
class CmsTokenKeySizesTest {

	private static final BouncyCastleProvider BC = new BouncyCastleProvider();

	private static final byte[] DATA = "Data for a key that never leaves the token. ".repeat(30)
		.getBytes(StandardCharsets.UTF_8);

	private static Pkcs11Token token;

	static List<String> aliases() {
		return TestToken.extraAliases();
	}

	@BeforeAll
	static void openToken() {
		token = Pkcs11Token.open(TestToken.config(), (info) -> TestToken.pin());
	}

	@AfterAll
	static void closeToken() {
		token.close();
	}

	@ParameterizedTest
	@MethodSource("aliases")
	void signsOnTheToken(String alias) {
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

	@ParameterizedTest
	@MethodSource("aliases")
	void decryptsOnTheToken(String alias) throws Exception {
		X509Certificate certificate = token.certificate(alias);
		boolean ec = certificate.getPublicKey() instanceof ECPublicKey;
		byte[] message;
		if (ec) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			CmsEncryptor.create()
				.encrypt(EncryptRequest.builder().recipients(CmsRecipient.certificate(certificate)).build(),
						new ByteArrayInputStream(DATA), out);
			message = out.toByteArray();
		}
		else {
			// SunPKCS11 has no OAEP, so the message uses PKCS#1 v1.5, which the key
			// handle must allow.
			CMSAuthEnvelopedDataGenerator generator = new CMSAuthEnvelopedDataGenerator();
			generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(certificate).setProvider(BC));
			message = generator
				.generate(new CMSProcessableByteArray(DATA),
						(OutputAEADEncryptor) new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM).setProvider(BC)
							.build())
				.getEncoded();
		}

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		CmsDecryptor.create()
			.decrypt(DecryptRequest.builder()
				.key(ec ? token.privateKey(alias) : token.privateKey(alias).allowPkcs1v15KeyTransport(), certificate)
				.build(), new ByteArrayInputStream(message), plain);

		assertThat(plain.toByteArray()).as(alias).isEqualTo(DATA);
	}

}
