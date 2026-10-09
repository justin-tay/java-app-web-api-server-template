package com.example.commons.security.session;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.logging.LoggingContextKeys;

/**
 * Binds a session to the User-Agent and client IP first observed on it, implementing the
 * OWASP Session Management Cheat Sheet's recommendation to bind the session ID to other
 * client properties (see docs/adr/0026). The two properties get different responses: a
 * User-Agent is not expected to change mid-session, so a change invalidates the session;
 * a client IP can change for innocent reasons (mobile/Wi-Fi roaming), so a change is only
 * logged as an anomaly, never invalidates the session, and the stored value moves to the
 * new one so the same change is not logged again on every later request.
 */
public class SessionBindingFilter extends OncePerRequestFilter {

	static final String USER_AGENT_ATTRIBUTE = SessionBindingFilter.class.getName() + ".USER_AGENT";

	static final String CLIENT_IP_ATTRIBUTE = SessionBindingFilter.class.getName() + ".CLIENT_IP";

	private final boolean hijackingProtection;

	private final boolean anomalyDetection;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public SessionBindingFilter(boolean hijackingProtection, boolean anomalyDetection,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.hijackingProtection = hijackingProtection;
		this.anomalyDetection = anomalyDetection;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null) {
			boolean invalidated = this.hijackingProtection && userAgentChanged(request, session);
			if (invalidated) {
				this.sessionLifecycleAuditLogger.logSessionDestroyed(session, "user_agent_mismatch");
				session.invalidate();
			}
			else if (this.anomalyDetection) {
				checkClientIpAnomaly(request, session);
			}
		}
		filterChain.doFilter(request, response);
	}

	private boolean userAgentChanged(HttpServletRequest request, HttpSession session) {
		String current = request.getHeader(HttpHeaders.USER_AGENT);
		Object bound = session.getAttribute(USER_AGENT_ATTRIBUTE);
		if (bound == null) {
			session.setAttribute(USER_AGENT_ATTRIBUTE, current);
			return false;
		}
		return !Objects.equals(bound, current);
	}

	private void checkClientIpAnomaly(HttpServletRequest request, HttpSession session) {
		String current = MDC.get(LoggingContextKeys.CLIENT_IP);
		if (current == null) {
			return;
		}
		Object bound = session.getAttribute(CLIENT_IP_ATTRIBUTE);
		if (bound == null) {
			session.setAttribute(CLIENT_IP_ATTRIBUTE, current);
			return;
		}
		if (!bound.equals(current)) {
			this.sessionLifecycleAuditLogger.logClientIpAnomaly(session, (String) bound);
			session.setAttribute(CLIENT_IP_ATTRIBUTE, current);
		}
	}

}
