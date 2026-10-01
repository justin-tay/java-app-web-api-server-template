package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ReviewWindowTest {

	@Test
	void threeMonthWindowsAreCalendarQuarters() {
		assertThat(ReviewWindow.containing(LocalDate.of(2026, 1, 1), 3))
			.isEqualTo(new ReviewWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
		assertThat(ReviewWindow.containing(LocalDate.of(2026, 5, 15), 3))
			.isEqualTo(new ReviewWindow(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)));
		assertThat(ReviewWindow.containing(LocalDate.of(2026, 12, 31), 3))
			.isEqualTo(new ReviewWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
	}

	@Test
	void aOneMonthWindowIsTheCalendarMonthIncludingALeapFebruary() {
		assertThat(ReviewWindow.containing(LocalDate.of(2028, 2, 10), 1))
			.isEqualTo(new ReviewWindow(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
		assertThat(ReviewWindow.containing(LocalDate.of(2027, 2, 10), 1))
			.isEqualTo(new ReviewWindow(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)));
	}

	@Test
	void aTwelveMonthWindowIsTheCalendarYear() {
		assertThat(ReviewWindow.containing(LocalDate.of(2026, 7, 4), 12))
			.isEqualTo(new ReviewWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
	}

	@Test
	void aWindowThatDoesNotDivideTheYearRunsOnAcrossTheNewYear() {
		ReviewWindow window = ReviewWindow.containing(LocalDate.of(2026, 12, 20), 5);

		assertThat(window).isEqualTo(new ReviewWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31)));
		assertThat(ReviewWindow.containing(LocalDate.of(2027, 1, 15), 5)).isEqualTo(window);
		assertThat(ReviewWindow.containing(LocalDate.of(2027, 2, 1), 5).start()).isEqualTo(LocalDate.of(2027, 2, 1));
	}

}
