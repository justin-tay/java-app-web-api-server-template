package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * A set of accounts that a reviewer confirms as a whole instead of one by one: the
 * suspended accounts, or the accounts removed since the previous review.
 */
public enum ReviewPopulation {

	SUSPENDED, REMOVED;

	/**
	 * Returns the lower case value used in the API.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static ReviewPopulation fromValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
