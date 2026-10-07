package com.example.commons.accounts.admin;

import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.commons.audit.Auditor;
import com.example.commons.security.authorization.LocalAuthorities;

/**
 * The authenticated user making a change: their name, as the {@link Auditor} records it,
 * and the permissions they hold. The permissions come from the security context, whose
 * local authorities {@code LocalAuthorityRefreshFilter} reloads from the local model on
 * every request.
 *
 * @param name the user's name
 * @param permissions the permissions the user holds, as {@code domain:action}
 */
public record Actor(String name, Set<String> permissions) {

	/**
	 * Returns the current user, or empty when no user is authenticated, such as for a
	 * change made by the application itself outside a request.
	 * @return the current user
	 */
	public static Optional<Actor> current() {
		String name = Auditor.current();
		if (Auditor.SYSTEM.equals(name)) {
			return Optional.empty();
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return Optional.of(new Actor(name, LocalAuthorities.names(authentication.getAuthorities())));
	}

	/**
	 * Returns whether the current user holds the permission. A change with no
	 * authenticated user is made by the application itself and is trusted.
	 * @param permission the permission, as {@code domain:action}
	 * @return whether the change is allowed
	 */
	public static boolean currentHolds(String permission) {
		return current().map(actor -> actor.permissions().contains(permission)).orElse(true);
	}

}
