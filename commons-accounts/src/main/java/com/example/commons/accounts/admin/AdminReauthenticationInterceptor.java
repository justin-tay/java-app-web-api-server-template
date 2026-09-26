package com.example.commons.accounts.admin;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.commons.security.authentication.oidc.RecentAuthentication;
import com.example.commons.web.problem.ReauthenticationRequiredException;

/**
 * Requires a recent login for every change made through the administration API: a
 * state-changing request from a user who authenticated longer than {@code maxAge} ago is
 * answered with a {@code reauthentication-required} problem instead of reaching the
 * controller (see docs/adr/0023). Reads are not affected, and neither are the session
 * revocation endpoints, which it registers as exclusions, so an administrator responding
 * to an incident is never delayed by a login round trip.
 */
public class AdminReauthenticationInterceptor implements HandlerInterceptor {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private final Duration maxAge;

	private final Clock clock;

	public AdminReauthenticationInterceptor(Duration maxAge, Clock clock) {
		this.maxAge = maxAge;
		this.clock = clock;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!SAFE_METHODS.contains(request.getMethod()) && !RecentAuthentication
			.isWithin(SecurityContextHolder.getContext().getAuthentication(), this.maxAge, this.clock)) {
			throw new ReauthenticationRequiredException(this.maxAge);
		}
		return true;
	}

}
