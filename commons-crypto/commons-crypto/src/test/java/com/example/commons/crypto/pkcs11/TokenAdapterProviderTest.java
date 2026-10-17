package com.example.commons.crypto.pkcs11;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter provider is handed to Bouncy Castle, never registered, and must refuse keys
 * that are not on a token.
 */
class TokenAdapterProviderTest {

	private static final TokenAdapterProvider ADAPTER = new TokenAdapterProvider(Security.getProvider("SunJCE"));

	@Test
	void isNotRegisteredWithTheJvm() {
		assertThat(Security.getProvider(TokenAdapterProvider.NAME)).isNull();
	}

	private static KeyPair softwareKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		return generator.generateKeyPair();
	}

	@Test
	void theKeyFactoryNamesItOffersGiveTheSameKeysAsTheJdksEcKeyFactory() throws Exception {
		KeyPair pair = softwareKeyPair();
		X509EncodedKeySpec spec = new X509EncodedKeySpec(pair.getPublic().getEncoded());

		for (String name : new String[] { "ECDH", "1.2.840.10045.2.1" }) {
			KeyFactory factory = KeyFactory.getInstance(name, ADAPTER);
			assertThat(factory.getProvider().getName()).as(name).isEqualTo(TokenAdapterProvider.NAME);
			assertThat(factory.generatePublic(spec)).as(name)
				.isEqualTo(KeyFactory.getInstance("EC").generatePublic(spec));
		}
	}

	@Test
	void theRsaCipherDecryptsInputThatLostItsLeadingZeroByte() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		Cipher encrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		encrypt.init(Cipher.ENCRYPT_MODE, pair.getPublic());
		byte[] message = "session key".getBytes(StandardCharsets.UTF_8);
		byte[] ciphertext = encrypt.doFinal(message);
		while (ciphertext[0] != 0) {
			ciphertext = encrypt.doFinal(message);
		}
		byte[] shortened = Arrays.copyOfRange(ciphertext, 1, ciphertext.length);

		Cipher decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding", ADAPTER);
		decrypt.init(Cipher.DECRYPT_MODE, pair.getPrivate());

		assertThat(decrypt.doFinal(shortened)).isEqualTo(message);
		assertThat(decrypt.doFinal(ciphertext)).isEqualTo(message);
	}

}
