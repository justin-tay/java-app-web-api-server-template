package com.example.commons.audit;

import java.util.Locale;

/**
 * Whether an audited change was made or a business rule refused it, recorded in the row
 * and logged as {@code event.outcome}.
 */
public enum AuditOutcome {

	SUCCESS, FAILURE;

	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
