package com.example.commons.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoExceptionTest {

	@Test
	void keepsTheMessageAndCause() {
		Throwable cause = new IllegalStateException("boom");

		CryptoException exception = new CryptoException("failed", cause);

		assertThat(exception).hasMessage("failed").hasCause(cause);
	}

	@Test
	void isUnchecked() {
		assertThat(new CryptoException("failed")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void everySubtypeIsACryptoException() {
		assertThat(new NoMatchingKeyException("none")).isInstanceOf(CryptoException.class);
		assertThat(new IntegrityException("bad")).isInstanceOf(CryptoException.class);
		assertThat(new TokenUnavailableException("gone", null)).isInstanceOf(CryptoException.class);
		assertThat(new PinRequiredException("pin")).isInstanceOf(CryptoException.class);
	}

}
