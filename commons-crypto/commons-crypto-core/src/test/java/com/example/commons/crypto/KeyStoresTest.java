package com.example.commons.crypto;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyStoresTest {

	private static final char[] PASSWORD = "changeit".toCharArray();

	private static Path resource(String name) throws Exception {
		return Path.of(KeyStoresTest.class.getResource("/" + name).toURI());
	}

	@Test
	void loadsAnRsaKeyFromAPkcs12File() throws Exception {
		try (PrivateKeyHandle handle = KeyStores.pkcs12(resource("rsa.p12"), PASSWORD, "rsa")) {
			assertThat(handle.key().getAlgorithm()).isEqualTo("RSA");
			assertThat(handle.provider()).isEmpty();
		}
	}

	@Test
	void loadsAnEcKeyFromAPkcs12File() throws Exception {
		try (PrivateKeyHandle handle = KeyStores.pkcs12(resource("ec.p12"), PASSWORD, "ec")) {
			assertThat(handle.key().getAlgorithm()).isEqualTo("EC");
		}
	}

	@Test
	void loadsAKeyFromAnOpenKeyStore() throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream in = KeyStoresTest.class.getResourceAsStream("/rsa.p12")) {
			keyStore.load(in, PASSWORD);
		}

		try (PrivateKeyHandle handle = KeyStores.keyStore(keyStore, "rsa", PASSWORD)) {
			assertThat(handle.key()).isInstanceOf(PrivateKey.class);
		}
	}

	@Test
	void aWrongStorePasswordIsACryptoExceptionThatDoesNotRevealThePassword() throws Exception {
		Path file = resource("rsa.p12");

		assertThatThrownBy(() -> KeyStores.pkcs12(file, "not-the-password".toCharArray(), "rsa"))
			.isInstanceOf(CryptoException.class)
			.hasMessageNotContaining("not-the-password");
	}

	@Test
	void anUnknownAliasIsACryptoException() throws Exception {
		Path file = resource("rsa.p12");

		assertThatThrownBy(() -> KeyStores.pkcs12(file, PASSWORD, "missing")).isInstanceOf(CryptoException.class)
			.hasMessageContaining("missing");
	}

	@Test
	void aMissingFileIsACryptoException(@TempDir Path directory) {
		Path file = directory.resolve("absent.p12");

		assertThatThrownBy(() -> KeyStores.pkcs12(file, PASSWORD, "rsa")).isInstanceOf(CryptoException.class);
	}

	@Test
	void theKeyIsUnavailableAfterClose() throws Exception {
		PrivateKeyHandle handle = KeyStores.pkcs12(resource("rsa.p12"), PASSWORD, "rsa");

		handle.close();
		handle.close();

		assertThatThrownBy(handle::key).isInstanceOf(IllegalStateException.class);
	}

}
