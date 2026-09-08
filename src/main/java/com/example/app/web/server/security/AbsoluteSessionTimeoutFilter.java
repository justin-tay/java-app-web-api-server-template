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

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public AbsoluteSessionTimeoutFilter(Duration timeout) {
		this(timeout, Clock.systemUTC(), new SessionLifecycleAuditLogger());
	}

	public AbsoluteSessionTimeoutFilter(Duration timeout, Clock clock) {
		this(timeout, clock, new SessionLifecycleAuditLogger());
	}

	public AbsoluteSessionTimeoutFilter(Duration timeout, Clock clock,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		Assert.notNull(timeout, "timeout cannot be null");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
		Assert.notNull(clock, "clock cannot be null");
		this.timeout = timeout;
		this.clock = clock;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && hasExpired(session)) {
			this.sessionLifecycleAuditLogger.logSessionDestroyed(session, "absolute_timeout");
			session.invalidate();
		}
		filterChain.doFilter(request, response);
	}

	private boolean hasExpired(HttpSession session) {
		Instant expiresAt = Instant.ofEpochMilli(session.getCreationTime()).plus(this.timeout);
		return !this.clock.instant().isBefore(expiresAt);
	}

}
