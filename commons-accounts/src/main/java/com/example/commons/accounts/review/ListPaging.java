package com.example.commons.accounts.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Sorts and pages a list that has already been filtered in memory. A review's rows mix
 * live and frozen values, so they are assembled first and sorted by what is shown. A task
 * holds one row per account, so the list is bounded.
 */
final class ListPaging {

	private ListPaging() {
	}

	/**
	 * Returns the requested page of the rows, sorted by the pageable's sort.
	 * @param rows the filtered rows
	 * @param pageable the page and sort, whose properties are keys of {@code comparators}
	 * @param comparators how to compare two rows by each sortable property
	 * @return the page
	 */
	static <T> Page<T> page(List<T> rows, Pageable pageable, Map<String, Comparator<T>> comparators) {
		List<T> sorted = new ArrayList<>(rows);
		Comparator<T> comparator = null;
		for (Sort.Order order : pageable.getSort()) {
			Comparator<T> next = comparators.get(order.getProperty());
			next = order.isAscending() ? next : next.reversed();
			comparator = comparator == null ? next : comparator.thenComparing(next);
		}
		if (comparator != null) {
			sorted.sort(comparator);
		}
		int from = (int) Math.min(pageable.getOffset(), sorted.size());
		int to = Math.min(from + pageable.getPageSize(), sorted.size());
		return new PageImpl<>(sorted.subList(from, to),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), sorted.size());
	}

	/**
	 * Compares text without regard to case, with missing values last.
	 */
	static <T> Comparator<T> text(Function<T, String> value) {
		return Comparator.comparing(value, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
	}

	/**
	 * Compares instants, with missing values last.
	 */
	static <T> Comparator<T> time(Function<T, Instant> value) {
		return Comparator.comparing(value, Comparator.nullsLast(Comparator.naturalOrder()));
	}

}
