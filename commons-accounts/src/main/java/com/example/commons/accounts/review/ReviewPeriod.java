package com.example.commons.accounts.review;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The period of an account review: one calendar month, which is a review month when its
 * number minus one is a multiple of the review interval, so an interval of three months
 * gives January, April, July and October, and twelve gives January only. The task for a
 * period starts on its first day and is due on its last (see docs/adr/0037).
 *
 * @param start the first day of the month
 * @param due the last day of the month
 */
public record ReviewPeriod(LocalDate start, LocalDate due) {

	/**
	 * Returns the review period that a date falls in.
	 * @param date the date
	 * @param intervalMonths the number of months between reviews: 1, 3, 6 or 12
	 * @return the period, or empty when the date's month is not a review month
	 */
	public static Optional<ReviewPeriod> containing(LocalDate date, int intervalMonths) {
		if ((date.getMonthValue() - 1) % intervalMonths != 0) {
			return Optional.empty();
		}
		LocalDate start = date.withDayOfMonth(1);
		return Optional.of(new ReviewPeriod(start, start.plusMonths(1).minusDays(1)));
	}

}
