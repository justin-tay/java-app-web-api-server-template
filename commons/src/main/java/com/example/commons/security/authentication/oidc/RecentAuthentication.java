package com.example.commons.security.authentication.oidc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Checks how recently an OpenID Connect user authenticated, from the {@code auth_time}
 * claim of their ID token.
 */
public final class RecentAuthentication {

	private RecentAuthentication() {
	}

	/**
	 * Returns whether the user authenticated no longer than {@code maxAge} ago. An
	 * authentication with no {@code auth_time}, including any that is not an OpenID
	 * Connect login, is treated as not recent, so the check fails closed.
	 * @param authentication the current authentication
	 * @param maxAge the greatest allowed time since authentication
	 * @param clock the clock to compare against
	 * @return whether the authentication is recent enough
	 */
	public static boolean isWithin(Authentication authentication, Duration maxAge, Clock clock) {
		if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
			return false;
		}
		Instant authenticatedAt = oidcUser.getAuthenticatedAt();
		return authenticatedAt != null && !authenticatedAt.plus(maxAge).isBefore(clock.instant());
	}

}
