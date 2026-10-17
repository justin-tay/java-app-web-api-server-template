package com.example.commons.crypto.pkcs11;

import java.nio.file.Path;

import com.example.commons.crypto.PinSupplier;
import com.example.commons.crypto.TokenUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that need no token. The tests that use a token are in
 * {@link Pkcs11TokenIntegrationTest}.
 */
class Pkcs11TokenTest {

	@Test
	void aMissingLibraryIsATokenUnavailableException() {
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("/does/not/exist/libpkcs11.so")).build();

		assertThatThrownBy(() -> Pkcs11Token.open(config, (PinSupplier) (token) -> "1234".toCharArray()))
			.isInstanceOf(TokenUnavailableException.class)
			.hasMessageNotContaining("1234");
	}

	@Test
	void thePinIsNotAskedForWhenTheLibraryCannotBeLoaded() {
		int[] calls = new int[1];
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("/does/not/exist/libpkcs11.so")).build();

		assertThatThrownBy(() -> Pkcs11Token.open(config, (token) -> {
			calls[0]++;
			return "1234".toCharArray();
		})).isInstanceOf(TokenUnavailableException.class);
		assertThat(calls[0]).isZero();
	}

}
