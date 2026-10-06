package com.example.commons.accounts.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The application settings the account lifecycle and the account review follow (see
 * docs/adr/0031 and docs/adr/0038).
 *
 * @param inactivity how inactive accounts are suspended and removed
 * @param review how often privileged and other accounts are reviewed
 */
public record Settings(@Valid @NotNull Inactivity inactivity, @Valid @NotNull Review review) {

	/**
	 * @param enabled whether the inactivity job runs
	 * @param suspendAfterDays days since an account was last in use before it is
	 * suspended
	 * @param removeAfterDays days since an account was last in use before it is removed
	 */
	public record Inactivity(boolean enabled, @Min(1) @Max(36500) int suspendAfterDays,
			@Min(1) @Max(36500) int removeAfterDays) {
	}

	/**
	 * @param enabled whether review tasks are created
	 * @param privilegedIntervalMonths the months between reviews of the privileged
	 * accounts: 1, 3, 6 or 12
	 * @param nonPrivilegedIntervalMonths the months between reviews of the other
	 * accounts: 1, 3, 6 or 12, and not fewer than the privileged interval
	 */
	public record Review(boolean enabled, @Min(1) @Max(12) int privilegedIntervalMonths,
			@Min(1) @Max(12) int nonPrivilegedIntervalMonths) {
	}

}
