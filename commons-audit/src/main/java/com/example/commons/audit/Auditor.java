package com.example.commons.audit;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The actor recorded against a change: the authenticated user's name, which is the same
 * value logged as {@code user.name}, or {@value #SYSTEM} when there is no authenticated
 * user, such as for a change made outside a request.
 *
 * <p>
 * Read from the security context directly rather than through Spring Data JPA's
 * {@code AuditorAware}, because enabling Spring Data auditing from a commons module would
 * clash with an application that enables it for its own entities.
 */
public final class Auditor {

	/**
	 * The actor recorded when no user is authenticated, and for rows seeded by
	 * migrations.
	 */
	public static final String SYSTEM = "system";

	private Auditor() {
	}

	/**
	 * Returns the current actor.
	 * @return the authenticated user's name, or {@value #SYSTEM}
	 */
	public static String current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return SYSTEM;
		}
		return authentication.getName();
	}

}
