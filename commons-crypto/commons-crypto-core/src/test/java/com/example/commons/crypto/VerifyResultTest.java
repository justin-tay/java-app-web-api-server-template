package com.example.commons.crypto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifyResultTest {

	private static SignatureResult result(SignatureStatus status) {
		return new SignatureResult("signer", status, Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Test
	void allValidWhenEverySignatureIsValid() {
		VerifyResult verifyResult = new VerifyResult(
				List.of(result(SignatureStatus.VALID), result(SignatureStatus.VALID)));

		assertThat(verifyResult.allValid()).isTrue();
	}

	@Test
	void notAllValidWhenOneIsInvalidOrFromAnUnknownSigner() {
		assertThat(new VerifyResult(List.of(result(SignatureStatus.VALID), result(SignatureStatus.INVALID))).allValid())
			.isFalse();
		assertThat(new VerifyResult(List.of(result(SignatureStatus.VALID), result(SignatureStatus.UNKNOWN_SIGNER)))
			.allValid()).isFalse();
	}

	@Test
	void noSignatureIsNotValid() {
		assertThat(new VerifyResult(List.of()).allValid()).isFalse();
	}

	@Test
	void theSignatureListIsACopyThatCannotBeChanged() {
		List<SignatureResult> source = new ArrayList<>(List.of(result(SignatureStatus.VALID)));

		VerifyResult verifyResult = new VerifyResult(source);
		source.clear();

		assertThat(verifyResult.signatures()).hasSize(1);
		assertThatThrownBy(() -> verifyResult.signatures().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void theSigningTimeMayBeUnknown() {
		assertThat(new SignatureResult("signer", SignatureStatus.VALID, null).signingTime()).isNull();
	}

}
