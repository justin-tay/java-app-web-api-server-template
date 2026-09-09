package com.example.app.web.server.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves an upstream request correlation identifier from a request.
 */
@FunctionalInterface
public interface RequestIdResolver {

	/**
	 * Resolves a request ID, or returns empty when the request has no upstream
	 * correlation identifier.
	 * @param request the current request
	 * @return the upstream request ID when available
	 */
	Optional<String> resolve(HttpServletRequest request);

	/**
	 * Returns the safe default resolver, which defers to an application-generated UUID.
	 * @return a resolver that never supplies an upstream request ID
	 */
	static RequestIdResolver none() {
		return request -> Optional.empty();
	}

}
