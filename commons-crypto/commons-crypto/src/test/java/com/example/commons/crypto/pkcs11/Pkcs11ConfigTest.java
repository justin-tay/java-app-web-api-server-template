package com.example.commons.crypto.pkcs11;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pkcs11ConfigTest {

	@Test
	void aLibraryIsRequired() {
		assertThatThrownBy(() -> Pkcs11Config.builder().build()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void theProviderConfigurationNamesTheLibraryAndAllowsEcdhSecrets() {
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("/usr/lib/softhsm/libsofthsm2.so")).build();

		String text = config.providerConfiguration("test-name");

		assertThat(text).startsWith("--")
			.contains("name=test-name")
			.contains("library=\""
					+ Path.of("/usr/lib/softhsm/libsofthsm2.so").toAbsolutePath().toString().replace('\\', '/') + "\"")
			.contains("CKO_SECRET_KEY")
			.contains("CKA_SENSITIVE = false")
			.contains("CKA_EXTRACTABLE = true");
	}

	@Test
	void aSlotListIndexIsPassedOn() {
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("lib.so")).slotListIndex(2).build();

		assertThat(config.providerConfiguration("x")).contains("slotListIndex=2");
	}

	@Test
	void aSlotIdIsPassedOnInsteadOfTheIndex() {
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("lib.so")).slot(77L).build();

		assertThat(config.providerConfiguration("x")).contains("slot=77").doesNotContain("slotListIndex");
	}

	@Test
	void aNegativeSlotListIndexIsRejected() {
		assertThatThrownBy(() -> Pkcs11Config.builder().library(Path.of("lib.so")).slotListIndex(-1))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
