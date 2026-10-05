package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Tests which months are review months for each interval, and that a period is that
 * month.
 */
class ReviewPeriodTest {

	@Test
	void everyThirdMonthStartingInJanuaryIsAReviewMonth() {
		assertThat(reviewMonths(3)).containsExactly(1, 4, 7, 10);
	}

	@Test
	void anIntervalOfOneMakesEveryMonthAReviewMonth() {
		assertThat(reviewMonths(1)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
	}

	@Test
	void anIntervalOfSixGivesJanuaryAndJuly() {
		assertThat(reviewMonths(6)).containsExactly(1, 7);
	}

	@Test
	void anIntervalOfTwelveGivesJanuaryOnly() {
		assertThat(reviewMonths(12)).containsExactly(1);
	}

	@Test
	void thePeriodIsTheReviewMonthItself() {
		ReviewPeriod october = ReviewPeriod.containing(LocalDate.of(2026, 10, 17), 3).orElseThrow();
		assertThat(october.start()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(october.due()).isEqualTo(LocalDate.of(2026, 10, 31));
		ReviewPeriod february = ReviewPeriod.containing(LocalDate.of(2028, 2, 3), 1).orElseThrow();
		assertThat(february.due()).isEqualTo(LocalDate.of(2028, 2, 29));
	}

	@Test
	void aMonthBetweenReviewMonthsHasNoPeriod() {
		assertThat(ReviewPeriod.containing(LocalDate.of(2026, 11, 30), 3)).isEmpty();
	}

	private static int[] reviewMonths(int interval) {
		return IntStream.rangeClosed(1, 12)
			.filter(month -> ReviewPeriod.containing(LocalDate.of(2026, month, 15), interval).isPresent())
			.toArray();
	}

}
