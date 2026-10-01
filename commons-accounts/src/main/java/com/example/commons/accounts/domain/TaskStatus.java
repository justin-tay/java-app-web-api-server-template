package com.example.commons.accounts.domain;

import java.util.Locale;

public enum TaskStatus {

	OPEN, COMPLETED;

	/**
	 * Returns the lower case value used in the API.
	 */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
