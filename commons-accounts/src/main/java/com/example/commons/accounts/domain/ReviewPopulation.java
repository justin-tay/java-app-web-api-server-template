package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * A set of accounts that a reviewer confirms as a whole instead of one by one: the
 * accounts removed since the previous review, which no longer exist to be decided on.
 */
public enum ReviewPopulation {

	REMOVED;

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
