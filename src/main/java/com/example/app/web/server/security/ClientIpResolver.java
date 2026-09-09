package com.example.app.web.server.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves a validated end-user client IP address from a request.
 */
@FunctionalInterface
public interface ClientIpResolver {

	/**
	 * Resolves a normalized IP address, or returns empty when the request does not
	 * contain a trusted client address.
	 * @param request the current request
	 * @return a normalized literal IP address when trusted
	 */
	Optional<String> resolve(HttpServletRequest request);

	/**
	 * Returns the safe default resolver, which does not infer a client address.
	 * @return a resolver that never supplies a client IP address
	 */
	static ClientIpResolver none() {
		return request -> Optional.empty();
	}

}
