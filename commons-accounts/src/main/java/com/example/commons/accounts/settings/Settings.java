package com.example.commons.accounts.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The application settings the account lifecycle and the account review follow (see
 * docs/adr/0031 and docs/adr/0032).
 *
 * @param inactivity how inactive accounts are suspended and removed
 * @param review how often accounts are reviewed
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
	 * @param intervalMonths the length of a review window in months
	 */
	public record Review(boolean enabled, @Min(1) @Max(12) int intervalMonths) {
	}

}
