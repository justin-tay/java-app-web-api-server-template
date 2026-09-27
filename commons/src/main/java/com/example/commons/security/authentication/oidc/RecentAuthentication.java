package com.example.commons.security.authentication.oidc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Checks how recently a user authenticated: from the {@code auth_time} claim of the ID
 * token of an OpenID Connect login, or from the time recorded in the session for a
 * passkey login.
 */
public final class RecentAuthentication {

	/**
	 * The session attribute, an {@link Instant}, in which a passkey login records when it
	 * happened.
	 */
	public static final String PASSKEY_AUTHENTICATED_AT_ATTRIBUTE = RecentAuthentication.class.getName()
			+ ".PASSKEY_AUTHENTICATED_AT";

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
		return isWithin(oidcUser.getAuthenticatedAt(), maxAge, clock);
	}

	/**
	 * Returns whether the user authenticated no longer than {@code maxAge} ago, by an
	 * OpenID Connect login or a passkey login. A login that recorded no time is treated
	 * as not recent, so the check fails closed.
	 * @param authentication the current authentication
	 * @param request the current request, whose session holds the time of a passkey login
	 * @param maxAge the greatest allowed time since authentication
	 * @param clock the clock to compare against
	 * @return whether the authentication is recent enough
	 */
	public static boolean isWithin(Authentication authentication, HttpServletRequest request, Duration maxAge,
			Clock clock) {
		if (authentication == null) {
			return false;
		}
		if (authentication.getPrincipal() instanceof OidcUser) {
			return isWithin(authentication, maxAge, clock);
		}
		HttpSession session = request.getSession(false);
		Object authenticatedAt = (session != null) ? session.getAttribute(PASSKEY_AUTHENTICATED_AT_ATTRIBUTE) : null;
		return authenticatedAt instanceof Instant instant && isWithin(instant, maxAge, clock);
	}

	private static boolean isWithin(Instant authenticatedAt, Duration maxAge, Clock clock) {
		return authenticatedAt != null && !authenticatedAt.plus(maxAge).isBefore(clock.instant());
	}

}
