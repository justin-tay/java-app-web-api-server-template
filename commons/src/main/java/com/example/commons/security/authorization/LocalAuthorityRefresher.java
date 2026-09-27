package com.example.commons.security.authorization;

import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Reloads the local {@code ROLE_} authorities of one kind of authentication for
 * {@link LocalAuthorityRefreshFilter}, so each way of logging in (OpenID Connect,
 * passkey) is refreshed, and revoked when its local user is disabled or deleted, the same
 * way.
 */
public interface LocalAuthorityRefresher {

	/**
	 * Returns whether this refresher handles the authentication.
	 * @param authentication the current authentication
	 * @return whether it is the kind of authentication this refresher refreshes
	 */
	boolean supports(Authentication authentication);

	/**
	 * Returns the username of an authentication this refresher supports.
	 * @param authentication the current authentication
	 * @return the username, or null if the authentication has none, in which case the
	 * user is deauthenticated
	 */
	String username(Authentication authentication);

	/**
	 * Returns the authentication with its {@code ROLE_} authorities replaced by the local
	 * ones, keeping its other authorities.
	 * @param authentication the current authentication
	 * @param localAuthorities the authorities the local model grants the user now
	 * @return the refreshed authentication
	 */
	Authentication refresh(Authentication authentication, Collection<GrantedAuthority> localAuthorities);

}
