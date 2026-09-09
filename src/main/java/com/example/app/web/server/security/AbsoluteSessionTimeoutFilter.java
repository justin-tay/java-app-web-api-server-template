package com.example.app.web.server.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Invalidates sessions that have reached their maximum lifetime.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

	private final Duration timeout;

	private final Clock clock;

	public AbsoluteSessionTimeoutFilter(Duration timeout) {
		this(timeout, Clock.systemUTC());
	}

	public AbsoluteSessionTimeoutFilter(Duration timeout, Clock clock) {
		Assert.notNull(timeout, "timeout cannot be null");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
		Assert.notNull(clock, "clock cannot be null");
		this.timeout = timeout;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && hasExpired(session)) {
			session.invalidate();
		}
		filterChain.doFilter(request, response);
	}

	private boolean hasExpired(HttpSession session) {
		Instant expiresAt = Instant.ofEpochMilli(session.getCreationTime()).plus(this.timeout);
		return !this.clock.instant().isBefore(expiresAt);
	}

}
