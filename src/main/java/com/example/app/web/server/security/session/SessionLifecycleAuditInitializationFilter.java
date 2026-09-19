package com.example.app.web.server.security.session;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures a session observed after request processing has its audit identifier
 * initialized.
 * <p>
 * A session can come into existence through more than one path (login, CSRF token
 * establishment, session-fixation renewal), so this checks unconditionally on every
 * request rather than reacting to one specific creation event; {@code
 * SessionLifecycleAuditLogger#logSessionCreatedIfNeeded} is idempotent, so the repeated
 * check is cheap and safe.
 */
public class SessionLifecycleAuditInitializationFilter extends OncePerRequestFilter {

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public SessionLifecycleAuditInitializationFilter(SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			if (request.getSession(false) != null) {
				this.sessionLifecycleAuditLogger.logSessionCreatedIfNeeded(request.getSession(false));
			}
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

}
