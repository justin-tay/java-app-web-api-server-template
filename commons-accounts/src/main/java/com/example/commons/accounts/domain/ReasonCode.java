package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * Why an account was suspended or removed. {@link #INACTIVE_ACCOUNT} is reserved for the
 * application's own inactivity job.
 */
public enum ReasonCode {

	INACTIVE_ACCOUNT, LEFT_ORGANISATION, NO_LONGER_REQUIRED, POLICY_VIOLATION, OTHER;

	/**
	 * Returns the lower case value used in the API and stored with the account.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static ReasonCode fromValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
