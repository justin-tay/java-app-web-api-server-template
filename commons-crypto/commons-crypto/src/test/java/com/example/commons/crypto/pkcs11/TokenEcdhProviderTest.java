package com.example.commons.crypto.pkcs11;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter provider is handed to Bouncy Castle, never registered, and must refuse keys
 * that are not on a token.
 */
class TokenEcdhProviderTest {

	private static final TokenEcdhProvider ADAPTER = new TokenEcdhProvider(Security.getProvider("SUN"));

	@Test
	void isNotRegisteredWithTheJvm() {
		assertThat(Security.getProvider(TokenEcdhProvider.NAME)).isNull();
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
			assertThat(factory.getProvider().getName()).as(name).isEqualTo(TokenEcdhProvider.NAME);
			assertThat(factory.generatePublic(spec)).as(name)
				.isEqualTo(KeyFactory.getInstance("EC").generatePublic(spec));
		}
	}

}
