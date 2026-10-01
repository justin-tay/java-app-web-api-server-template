package com.example.commons.accounts.review;

import java.time.LocalDate;

/**
 * A review window: a period of N whole calendar months aligned to January, so three
 * months gives January to March, April to June, and so on, and one month gives each
 * calendar month. The task for a window starts on its first day and is due on its last
 * (see docs/adr/0032).
 *
 * @param start the first day of the window
 * @param due the last day of the window
 */
public record ReviewWindow(LocalDate start, LocalDate due) {

	/**
	 * Returns the window that contains a date.
	 * @param date the date
	 * @param months the length of a window in months, at least 1
	 * @return the window
	 */
	public static ReviewWindow containing(LocalDate date, int months) {
		int monthIndex = date.getYear() * 12 + (date.getMonthValue() - 1);
		int startIndex = Math.floorDiv(monthIndex, months) * months;
		LocalDate start = LocalDate.of(Math.floorDiv(startIndex, 12), Math.floorMod(startIndex, 12) + 1, 1);
		return new ReviewWindow(start, start.plusMonths(months).minusDays(1));
	}

}
