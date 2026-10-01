package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * Where a user is in their life with the application, derived from the account status and
 * whether the user has ever signed in (see docs/adr/0028 and docs/adr/0031).
 */
public enum UserStatus {

	/** Active, and has signed in at least once. */
	ACTIVE,

	/** Suspended, by an administrator, a reviewer, or the inactivity job. */
	SUSPENDED,

	/** Active, but has never signed in. */
	PENDING;

	public static UserStatus of(AppUser user) {
		if (user.getStatus() == AccountStatus.SUSPENDED) {
			return SUSPENDED;
		}
		return user.getLastLoginAt() == null ? PENDING : ACTIVE;
	}

	/**
	 * Returns the lower case value used in the API.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static UserStatus fromValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
