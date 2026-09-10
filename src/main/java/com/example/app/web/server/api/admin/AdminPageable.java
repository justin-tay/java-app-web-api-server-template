package com.example.app.web.server.api.admin;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.app.web.server.api.BadRequestException;

final class AdminPageable {

	private AdminPageable() {
	}

	static PageRequest create(int page, int size, String sort, Set<String> allowed, String defaultProperty) {
		String[] values = sort == null ? new String[] { defaultProperty, "asc" } : sort.split(",", 2);
		if (!allowed.contains(values[0])) {
			throw new BadRequestException("Unsupported sort property.");
		}
		Sort.Direction direction = values.length == 2 ? Sort.Direction.fromOptionalString(values[1])
			.orElseThrow(() -> new BadRequestException("Unsupported sort direction.")) : Sort.Direction.ASC;
		return PageRequest.of(page, size, Sort.by(direction, values[0]));
	}

}
