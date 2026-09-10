package com.example.app.web.server.security.session;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Expires a user's active sessions when something their already-established authorities
 * depend on changes: account disablement, deletion, or a change to their group
 * membership. Without this, a user keeps their old authorities, or continues using a
 * disabled or deleted account, until the session's own idle or absolute timeout is
 * reached.
 */
@Component
public class SessionRevocationService {

	private final SessionRegistry sessionRegistry;

	private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public SessionRevocationService(SessionRegistry sessionRegistry,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.sessionRegistry = sessionRegistry;
		this.sessionRepository = sessionRepository;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	/**
	 * Expires every active session belonging to the given username, so each stops working
	 * the next time its owner uses it instead of continuing with now-stale authorities.
	 * This reuses the same concurrent-session-expiry mechanism Spring Security already
	 * applies when a second login supersedes a session, so the affected user sees the
	 * same {@code session-expired} response.
	 * @param username the local username whose sessions should be revoked
	 * @param reason the controlled revocation reason recorded in the audit log
	 */
	public void revoke(String username, String reason) {
		for (SessionInformation sessionInformation : this.sessionRegistry.getAllSessions(username, false)) {
			this.sessionLifecycleAuditLogger
				.logSessionDestroyed(this.sessionRepository.findById(sessionInformation.getSessionId()), reason);
			sessionInformation.expireNow();
		}
	}

}
