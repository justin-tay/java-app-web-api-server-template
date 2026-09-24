package com.example.commons.security.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * Ends the local sessions named by a validated OIDC Back-Channel Logout token by deleting
 * them from the Spring Session repository directly.
 * <p>
 * Spring Security's default {@code OidcBackChannelLogoutHandler} instead makes an
 * internal HTTP request back to the application carrying the session ID in a cookie
 * (named {@code JSESSIONID} unless configured). That cannot resume a session here: Spring
 * Session's cookie is named {@code id} and its value is Base64-encoded, while the handler
 * sends the raw session ID, so the request resumes nothing and the session survives.
 * Deleting through the repository also avoids depending on the application being able to
 * call itself at the URL the provider used.
 */
public class SessionRepositoryOidcBackChannelLogoutHandler implements LogoutHandler {

	static final String TERMINATION_REASON = "back_channel_logout";

	private final OidcSessionRegistry oidcSessionRegistry;

	private final SessionRepository<? extends Session> sessionRepository;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public SessionRepositoryOidcBackChannelLogoutHandler(OidcSessionRegistry oidcSessionRegistry,
			SessionRepository<? extends Session> sessionRepository,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.oidcSessionRegistry = oidcSessionRegistry;
		this.sessionRepository = sessionRepository;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof OidcLogoutToken logoutToken)) {
			return;
		}
		for (OidcSessionInformation sessionInformation : this.oidcSessionRegistry
			.removeSessionInformation(logoutToken)) {
			String sessionId = sessionInformation.getSessionId();
			Session session = this.sessionRepository.findById(sessionId);
			if (session != null) {
				this.sessionLifecycleAuditLogger.logSessionDestroyed(session, TERMINATION_REASON);
				this.sessionRepository.deleteById(sessionId);
			}
		}
	}

}
