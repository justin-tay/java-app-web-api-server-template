package com.example.app.web.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

/** Records the local session before Spring Security invalidates it on logout. */
public class SessionLifecycleLogoutHandler implements LogoutHandler {

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public SessionLifecycleLogoutHandler(SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		this.sessionLifecycleAuditLogger.logSessionDestroyed(request.getSession(false), "logout");
	}

}
