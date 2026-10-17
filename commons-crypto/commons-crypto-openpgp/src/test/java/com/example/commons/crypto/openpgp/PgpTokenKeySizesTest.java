package com.example.commons.crypto.openpgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;
import com.example.commons.crypto.pkcs11.Pkcs11Token;
import com.example.commons.crypto.testsupport.TestToken;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenPGP with the other keys on the token (bigger RSA keys, other curves), named by
 * PKCS11_EXTRA_ALIASES. See src/test/docker/pkcs11 in commons-crypto.
 */
@EnabledIfEnvironmentVariable(named = "PKCS11_EXTRA_ALIASES", matches = ".+")
class PgpTokenKeySizesTest {

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
	void signsOnTheToken(String alias) throws Exception {
		PgpPublicKeys publicKeys = TokenKeyRings.ring(token.certificate(alias).getPublicKey(), KeyFlags.SIGN_DATA);
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
	}

	@ParameterizedTest
	@MethodSource("aliases")
	void decryptsOnTheToken(String alias) throws Exception {
		PgpPublicKeys publicKeys = TokenKeyRings.ring(token.certificate(alias).getPublicKey(), TokenKeyRings.ENCRYPT);
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.encrypt(EncryptRequest.builder().recipients(publicKeys).build(), new ByteArrayInputStream(DATA), message);

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		PgpDecryptor.create()
			.decrypt(DecryptRequest.builder().keys(PgpSecretKeys.token(token.privateKey(alias), publicKeys)).build(),
					new ByteArrayInputStream(message.toByteArray()), plain);

		assertThat(plain.toByteArray()).as(alias).isEqualTo(DATA);
	}

}
