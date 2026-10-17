package com.example.commons.crypto.pkcs11;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter provider is registered for the whole JVM, so it must stay out of the way of
 * keys that are not on a token.
 */
class TokenEcdhProviderTest {

	@BeforeAll
	static void register() {
		TokenEcdhProvider.register();
	}

	private static KeyPair softwareKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		return generator.generateKeyPair();
	}

	@Test
	void isRegisteredOnceAtTheFrontOfTheProviderList() {
		TokenEcdhProvider.register();

		assertThat(Security.getProviders()[0].getName()).isEqualTo(TokenEcdhProvider.NAME);
		long registrations = java.util.Arrays.stream(Security.getProviders())
			.filter((provider) -> provider.getName().equals(TokenEcdhProvider.NAME))
			.count();
		assertThat(registrations).isEqualTo(1);
	}

	@Test
	void doesNotTakeASoftwareKeyForAKeyAgreementWithAKeyDerivationFunction() throws Exception {
		KeyPair pair = softwareKeyPair();
		KeyAgreement agreement = KeyAgreement.getInstance("ECDHwithSHA256KDF");

		assertThatThrownBy(() -> agreement.init(pair.getPrivate())).isInstanceOf(InvalidKeyException.class);
	}

	@Test
	void theKeyFactoryNamesItOffersGiveTheSameKeysAsTheJdksEcKeyFactory() throws Exception {
		KeyPair pair = softwareKeyPair();
		X509EncodedKeySpec spec = new X509EncodedKeySpec(pair.getPublic().getEncoded());

		for (String name : new String[] { "ECDH", "1.2.840.10045.2.1" }) {
			KeyFactory factory = KeyFactory.getInstance(name);
			assertThat(factory.getProvider().getName()).as(name).isEqualTo(TokenEcdhProvider.NAME);
			assertThat(factory.generatePublic(spec)).as(name)
				.isEqualTo(KeyFactory.getInstance("EC").generatePublic(spec));
		}
	}

}
