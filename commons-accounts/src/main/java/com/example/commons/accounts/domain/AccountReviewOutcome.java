package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * What a reviewer decided about an active account in an account review: still to be
 * decided, confirmed as needed with correct groups, confirmed after the groups were
 * edited, or removed (see docs/adr/0037).
 */
public enum AccountReviewOutcome {

	PENDING, CONFIRMED, CONFIRMED_GROUPS_EDITED, REMOVED;

	/**
	 * Returns the lower case value used in the API.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static AccountReviewOutcome fromValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
