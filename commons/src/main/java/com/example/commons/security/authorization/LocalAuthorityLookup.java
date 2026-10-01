package com.example.commons.security.authorization;

import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;

/**
 * Looks up the authorities the application grants a user, from the application's own
 * local user, group, and role model rather than from identity provider claims (see
 * docs/adr/0005).
 *
 * <p>
 * The commons module calls this at login, through
 * {@code LocalAuthoritiesOidcUserService}, and again on every authenticated request,
 * through {@link LocalAuthorityRefreshFilter}. Every application must define exactly one
 * bean of this type; the commons module fails startup when there is none, rather than
 * falling back to trusting the identity provider's roles. An application that
 * deliberately relies on identity provider roles only defines an implementation that says
 * so.
 */
@FunctionalInterface
public interface LocalAuthorityLookup {

	/**
	 * Returns the authorities of the enabled local user with the given username.
	 * @param username the username from the identity provider's
	 * {@code user-name-attribute} claim
	 * @return the user's {@code ROLE_} authorities, or empty when there is no enabled
	 * local user with that username, which denies the login or ends the session
	 */
	Optional<Collection<GrantedAuthority>> findAuthorities(String username);

}
