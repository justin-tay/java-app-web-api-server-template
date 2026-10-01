package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * What a reviewer decided about an account in a review: still to be decided, verified as
 * still needed, or removed.
 */
public enum ReviewStatus {

	PENDING_VERIFICATION, VERIFIED, REMOVED;

	/**
	 * Returns the lower case value used in the API.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static ReviewStatus fromValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
