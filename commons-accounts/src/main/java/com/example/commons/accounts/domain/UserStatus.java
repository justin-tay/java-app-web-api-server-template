package com.example.commons.accounts.domain;

import java.util.Locale;

/**
 * Where a user is in their life with the application, derived from whether the account is
 * enabled and whether the user has ever signed in (see docs/adr/0028).
 */
public enum UserStatus {

	/** Enabled, and has signed in at least once. */
	ACTIVE,

	/** Disabled by an administrator. */
	DISABLED,

	/** Enabled, but has never signed in. */
	PENDING;

	public static UserStatus of(AppUser user) {
		if (!user.isEnabled()) {
			return DISABLED;
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
