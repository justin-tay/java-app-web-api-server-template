package com.example.commons.crypto;

import java.io.IOException;
import java.security.ProviderException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenFailuresTest {

	@Test
	void aRemovedTokenIsRecognisedAnywhereInTheCauseChain() {
		Throwable failure = new IOException("load failed",
				new ProviderException("sign() failed", new RuntimeException("CKR_DEVICE_REMOVED")));

		assertThat(TokenFailures.unavailable(failure)).isPresent();
		assertThat(TokenFailures.unavailable(failure).get()).hasCause(failure);
	}

	@Test
	void everyCodeThatMeansTheTokenIsGoneIsRecognised() {
		for (String code : new String[] { "CKR_TOKEN_NOT_PRESENT", "CKR_DEVICE_REMOVED", "CKR_DEVICE_ERROR",
				"CKR_SLOT_ID_INVALID", "CKR_TOKEN_NOT_RECOGNIZED", "CKR_SESSION_HANDLE_INVALID",
				"CKR_SESSION_CLOSED" }) {
			assertThat(TokenFailures.unavailable(new ProviderException("failed: " + code))).as(code).isPresent();
		}
	}

	@Test
	void otherFailuresAreNot() {
		assertThat(TokenFailures.unavailable(new ProviderException("CKR_PIN_INCORRECT"))).isEmpty();
		assertThat(TokenFailures.unavailable(new IOException("disk full", null))).isEmpty();
		assertThat(TokenFailures.unavailable(new IOException((String) null))).isEmpty();
	}

	@Test
	void translateKeepsAnOrdinaryFailureAsItIs() {
		IllegalStateException failure = new IllegalStateException("closed");

		assertThat(TokenFailures.translate(failure)).isSameAs(failure);
	}

	@Test
	void translateTurnsARemovedTokenIntoTheUnavailableException() {
		ProviderException failure = new ProviderException("CKR_DEVICE_REMOVED");

		assertThat(TokenFailures.translate(failure)).isInstanceOf(TokenUnavailableException.class).hasCause(failure);
	}

	@Test
	void translateWrapsACheckedFailureWithTheMessage() {
		IOException failure = new IOException("broken");

		CryptoException translated = TokenFailures.translate(failure, "Cannot sign the data");

		assertThat(translated).isExactlyInstanceOf(CryptoException.class).hasMessage("Cannot sign the data");
		assertThat(TokenFailures.translate(new IOException("CKR_DEVICE_REMOVED"), "x"))
			.isInstanceOf(TokenUnavailableException.class);
	}

}
