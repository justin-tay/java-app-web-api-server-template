package com.example.app.web.server.security.session;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import com.example.app.web.server.api.ProblemTypes;

/**
 * Returns an appropriate response when a concurrent-session limit expires a session.
 */
public class ContentNegotiatingSessionExpiredStrategy implements SessionInformationExpiredStrategy {

	private static final String SESSION_EXPIRED_PROBLEM_DETAIL = "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"Your session is no longer active. Sign in again.\"}"
		.formatted(ProblemTypes.SESSION_EXPIRED, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
				HttpStatus.UNAUTHORIZED.value());

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public ContentNegotiatingSessionExpiredStrategy(SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
		HttpServletRequest request = event.getRequest();
		HttpServletResponse response = event.getResponse();
		this.sessionLifecycleAuditLogger.logSessionDestroyed(request.getSession(false), "concurrent_session");
		if (acceptsHtml(request)) {
			response.sendRedirect(request.getContextPath() + "/login?session-expired");
			return;
		}
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(SESSION_EXPIRED_PROBLEM_DETAIL);
	}

	private boolean acceptsHtml(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
	}

}
