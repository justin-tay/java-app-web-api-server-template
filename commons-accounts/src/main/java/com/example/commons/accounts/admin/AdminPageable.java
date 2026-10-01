package com.example.commons.accounts.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.commons.web.problem.BadRequestException;

public final class AdminPageable {

	static final int MAX_SORTS = 3;

	private AdminPageable() {
	}

	/**
	 * Builds a page request from repeated {@code sort} values, each {@code property} or
	 * {@code property,asc|desc}. Every property must be allowed and appear only once.
	 */
	public static PageRequest create(int page, int size, String[] sort, Set<String> allowed, String defaultProperty) {
		String[] requested = sort == null || sort.length == 0 ? new String[] { defaultProperty } : sort;
		if (requested.length > MAX_SORTS) {
			throw new BadRequestException("Too many sort properties.");
		}
		Set<String> seen = new HashSet<>();
		List<Sort.Order> orders = new ArrayList<>();
		for (String value : requested) {
			String[] values = value.split(",", 2);
			if (!allowed.contains(values[0])) {
				throw new BadRequestException("Unsupported sort property.");
			}
			if (!seen.add(values[0])) {
				throw new BadRequestException("Duplicate sort property.");
			}
			Sort.Direction direction = values.length == 2 ? Sort.Direction.fromOptionalString(values[1])
				.orElseThrow(() -> new BadRequestException("Unsupported sort direction.")) : Sort.Direction.ASC;
			orders.add(new Sort.Order(direction, values[0]));
		}
		return PageRequest.of(page, size, Sort.by(orders));
	}

}
