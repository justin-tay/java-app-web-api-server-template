package com.example.commons.accounts.admin;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.commons.accounts.domain.Auditor;

/**
 * The authenticated administrator making a change: their name, as the {@link Auditor}
 * records it, and the stored names of the roles they hold. The roles come from the
 * security context, whose {@code ROLE_} authorities {@code LocalAuthorityRefreshFilter}
 * reloads from the local model on every request.
 *
 * @param name the administrator's name
 * @param roles the stored names of the administrator's roles, without the {@code ROLE_}
 * prefix
 */
record Actor(String name, Set<String> roles) {

	private static final String ROLE_PREFIX = "ROLE_";

	/**
	 * Returns the current administrator, or empty when no user is authenticated, such as
	 * for a change made by the application itself outside a request.
	 * @return the current administrator
	 */
	static Optional<Actor> current() {
		String name = Auditor.current();
		if (Auditor.SYSTEM.equals(name)) {
			return Optional.empty();
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Set<String> roles = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority.startsWith(ROLE_PREFIX))
			.map(authority -> authority.substring(ROLE_PREFIX.length()))
			.collect(Collectors.toUnmodifiableSet());
		return Optional.of(new Actor(name, roles));
	}

}
