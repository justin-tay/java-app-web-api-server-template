package com.example.commons.crypto.openpgp;

import com.example.commons.crypto.testsupport.RecordingProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.util.Optional;

import com.example.commons.crypto.PrivateKeyHandle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A key handle that is not from the library's own token support supplies its own
 * provider, which is then used for an EC key too, for the key agreement as well as for
 * signing.
 */
class PgpCustomProviderTest {

	private static final byte[] DATA = "a key behind a provider of its own".getBytes(StandardCharsets.UTF_8);

	private static PrivateKeyHandle handle(KeyPair keyPair, Provider provider) {
		return new PrivateKeyHandle() {
			@Override
			public PrivateKey key() {
				return keyPair.getPrivate();
			}

			@Override
			public Optional<Provider> provider() {
				return Optional.of(provider);
			}

			@Override
			public void close() {
			}
		};
	}

	@Test
	void decryptsWithAnEcKeyThroughTheHandlesOwnProvider() throws Exception {
		KeyPair keyPair = TokenKeyRings.softwareKeyPair(true);
		PgpPublicKeys publicKeys = TokenKeyRings.ring(keyPair.getPublic(), TokenKeyRings.ENCRYPT);
		RecordingProvider provider = new RecordingProvider(new BouncyCastleProvider());
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Pgp.encrypt(new ByteArrayInputStream(DATA), encrypted, publicKeys);

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		Pgp.decrypt(new ByteArrayInputStream(encrypted.toByteArray()), plain,
				PgpSecretKeys.token(handle(keyPair, provider), publicKeys));

		assertThat(plain.toByteArray()).isEqualTo(DATA);
		assertThat(provider.requests).contains("KeyAgreement");
	}

}
