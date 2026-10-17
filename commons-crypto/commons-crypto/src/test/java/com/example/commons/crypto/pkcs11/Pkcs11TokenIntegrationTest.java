package com.example.commons.crypto.pkcs11;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.commons.crypto.KeyStores;
import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.PrivateKeyHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses a PKCS#11 token that the script src/test/docker/pkcs11/run-tests.sh sets up in a
 * container. It holds an RSA key and its certificate under the alias k, and the PIN is
 * 1234. The tests are skipped when PKCS11_LIBRARY is not set.
 */
@EnabledIfEnvironmentVariable(named = "PKCS11_LIBRARY", matches = ".+")
class Pkcs11TokenIntegrationTest {

	private static final String ALIAS = TestToken.RSA_ALIAS;

	private static Pkcs11Config config() {
		return TestToken.config();
	}

	private static byte[] sign(PrivateKeyHandle handle, byte[] data) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA", handle.provider().orElseThrow());
		signature.initSign(handle.key());
		signature.update(data);
		return signature.sign();
	}

	private static boolean verify(X509Certificate certificate, byte[] data, byte[] signed) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initVerify(certificate.getPublicKey());
		signature.update(data);
		return signature.verify(signed);
	}

	@Test
	void signsOnTheTokenWithTheRightPin() throws Exception {
		byte[] data = "hello token".getBytes();
		try (Pkcs11Token token = Pkcs11Token.open(config(), (info) -> TestToken.pin())) {
			X509Certificate certificate = token.certificate(ALIAS);

			try (PrivateKeyHandle handle = token.privateKey(ALIAS)) {
				assertThat(handle.provider()).isPresent();
				assertThat(verify(certificate, data, sign(handle, data))).isTrue();
			}
		}
	}

	@Test
	void listsTheAliasesOnTheToken() throws Exception {
		try (Pkcs11Token token = Pkcs11Token.open(config(), (info) -> TestToken.pin())) {
			assertThat(token.aliases()).contains(ALIAS, TestToken.EC_ALIAS);
		}
	}

	@Test
	void theKeyStoresShortcutOwnsTheToken() throws Exception {
		byte[] data = "shortcut".getBytes();
		PrivateKeyHandle handle = KeyStores.pkcs11(config(), (info) -> TestToken.pin(), ALIAS);

		assertThat(sign(handle, data)).isNotEmpty();
		PrivateKey key = handle.key();
		handle.close();

		assertThat(key).isNotNull();
		assertThatThrownBy(handle::key).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aWrongPinIsAskedForOnceAndNeverRetried() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> Pkcs11Token.open(config(), (info) -> {
			calls.incrementAndGet();
			return "0000".toCharArray();
		})).isInstanceOf(PinRequiredException.class).hasMessageNotContaining("0000");
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void aMissingPinIsAPinRequiredException() {
		assertThatThrownBy(() -> Pkcs11Token.open(config(), (info) -> null)).isInstanceOf(PinRequiredException.class);
	}

	@Test
	void anUnknownAliasIsACryptoException() throws Exception {
		try (Pkcs11Token token = Pkcs11Token.open(config(), (info) -> TestToken.pin())) {
			assertThatThrownBy(() -> token.privateKey("missing"))
				.isInstanceOf(com.example.commons.crypto.CryptoException.class)
				.hasMessageContaining("missing");
		}
	}

	@Test
	void theKeyIsUnavailableOnceTheTokenIsClosed() throws Exception {
		Pkcs11Token token = Pkcs11Token.open(config(), (info) -> TestToken.pin());
		PrivateKeyHandle handle = token.privateKey(ALIAS);

		token.close();
		token.close();

		assertThatThrownBy(handle::key).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> token.privateKey(ALIAS)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void severalThreadsCanSignWithOneHandle() throws Exception {
		byte[] data = "parallel".getBytes();
		ExecutorService executor = Executors.newFixedThreadPool(8);
		try (Pkcs11Token token = Pkcs11Token.open(config(), (info) -> TestToken.pin());
				PrivateKeyHandle handle = token.privateKey(ALIAS)) {
			X509Certificate certificate = token.certificate(ALIAS);
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < 40; i++) {
				Callable<Boolean> task = () -> verify(certificate, data, sign(handle, data));
				results.add(executor.submit(task));
			}
			for (Future<Boolean> result : results) {
				assertThat(result.get()).isTrue();
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

}
