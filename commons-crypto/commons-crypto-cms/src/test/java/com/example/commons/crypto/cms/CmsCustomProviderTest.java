package com.example.commons.crypto.cms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
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
class CmsCustomProviderTest {

	private static final byte[] DATA = "a key behind a provider of its own".getBytes(StandardCharsets.UTF_8);

	private static PrivateKeyHandle handle(PrivateKey key, Provider provider) {
		return new PrivateKeyHandle() {
			@Override
			public PrivateKey key() {
				return key;
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
		X509Certificate certificate = TestKeys.certificate("ec");
		RecordingProvider provider = new RecordingProvider(new BouncyCastleProvider());
		ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
		Cms.encrypt(new ByteArrayInputStream(DATA), encrypted, certificate);

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		try (PrivateKeyHandle software = TestKeys.handle("ec")) {
			Cms.decrypt(new ByteArrayInputStream(encrypted.toByteArray()), plain, handle(software.key(), provider),
					certificate);
		}

		assertThat(plain.toByteArray()).isEqualTo(DATA);
		assertThat(provider.requests).contains("KeyAgreement");
	}

}
