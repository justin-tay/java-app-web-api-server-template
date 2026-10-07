package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * The status an account had when it was added to an account review, which decides the
 * list its item belongs to. An item is live while its account still has that status (see
 * docs/adr/0039).
 */
public enum AccountReviewCategory {

	ACTIVE(AccountStatus.ACTIVE), SUSPENDED(AccountStatus.SUSPENDED);

	private final AccountStatus status;

	AccountReviewCategory(AccountStatus status) {
		this.status = status;
	}

	/**
	 * Returns the account status that puts an account in this category.
	 */
	public AccountStatus status() {
		return this.status;
	}

	/**
	 * Returns the lower case value used in the API.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static AccountReviewCategory fromValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
