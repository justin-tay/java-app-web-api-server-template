package com.example.app.web.server.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves CloudFront's request correlation identifier.
 */
public final class CloudFrontRequestIdResolver implements RequestIdResolver {

	private static final String HEADER_NAME = "X-Amz-Cf-Id";

	private static final int MAXIMUM_REQUEST_ID_LENGTH = 256;

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		String requestId = request.getHeader(HEADER_NAME);
		if (requestId == null || requestId.isBlank() || requestId.length() > MAXIMUM_REQUEST_ID_LENGTH
				|| requestId.chars().anyMatch(character -> character <= ' ' || character > '~')) {
			return Optional.empty();
		}
		return Optional.of(requestId);
	}

}
