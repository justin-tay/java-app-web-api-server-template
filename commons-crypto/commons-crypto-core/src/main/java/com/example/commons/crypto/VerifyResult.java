package com.example.commons.crypto;

import java.util.List;

/**
 * Every signature found in a message, each with its own outcome, so an invalid signature
 * cannot be missed by forgetting a check.
 *
 * @param signatures the signatures, in the order they appear in the message
 */
public record VerifyResult(List<SignatureResult> signatures) {

	public VerifyResult {
		signatures = List.copyOf(signatures);
	}

	/**
	 * Whether the message has at least one signature and every signature is valid. A
	 * message with no signature is not valid.
	 * @return true if there is a signature and none is invalid or from an unknown signer
	 */
	public boolean allValid() {
		return !this.signatures.isEmpty()
				&& this.signatures.stream().allMatch((signature) -> signature.status() == SignatureStatus.VALID);
	}

}
