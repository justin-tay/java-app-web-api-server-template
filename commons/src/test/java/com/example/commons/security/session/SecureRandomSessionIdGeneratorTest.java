package com.example.commons.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureRandomSessionIdGeneratorTest {

	private final SecureRandomSessionIdGenerator generator = new SecureRandomSessionIdGenerator();

	@Test
	void generatesUnpaddedBase64UrlIdsOf256Bits() {
		String id = this.generator.generate();

		assertThat(id).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(Base64.getUrlDecoder().decode(id)).hasSize(32);
	}

	@Test
	void generatesADifferentIdEachTime() {
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			ids.add(this.generator.generate());
		}

		assertThat(ids).hasSize(1000);
	}

}
