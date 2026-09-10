package com.example.app.web.server.security.session;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Records session lifecycle events without ever recording the browser's session
 * credential. The audit identifier is a separate, random server-side session attribute.
 */
@Component
public class SessionLifecycleAuditLogger {

	static final String AUDIT_SESSION_ID_ATTRIBUTE = SessionLifecycleAuditLogger.class.getName() + ".AUDIT_SESSION_ID";

	private static final Logger LOGGER = LoggerFactory.getLogger(SessionLifecycleAuditLogger.class);

	/**
	 * Creates and records the application-local audit identifier when it is first needed
	 * for a servlet session.
	 * @param session the servlet session
	 */
	public void logSessionCreatedIfNeeded(HttpSession session) {
		if (session == null) {
			return;
		}
		if (auditSessionId(session) != null) {
			return;
		}
		String auditSessionId = UUID.randomUUID().toString();
		session.setAttribute(AUDIT_SESSION_ID_ATTRIBUTE, auditSessionId);
		log("create_session", "start", auditSessionId, null);
	}

	/**
	 * Records Spring Security's session-fixation ID rotation. The old and new browser
	 * session IDs are intentionally not included.
	 * @param event the fixation-protection event
	 * @param session the rotated servlet session
	 */
	public void logSessionRenewed(SessionFixationProtectionEvent event, HttpSession session) {
		logSessionCreatedIfNeeded(session);
		log("renew_session", "info", auditSessionId(session), null);
	}

	/**
	 * Records a session invalidation only when its audit identifier was established
	 * earlier. This prevents a supplied, unknown session credential becoming log data.
	 * @param session the session being invalidated
	 * @param reason the controlled invalidation reason
	 */
	public void logSessionDestroyed(HttpSession session, String reason) {
		if (session == null) {
			return;
		}
		String auditSessionId = auditSessionId(session);
		if (auditSessionId != null) {
			log("destroy_session", "end", auditSessionId, reason);
		}
	}

	/**
	 * Records an administrative session revocation for a Spring Session {@link Session}
	 * looked up outside a servlet request, such as when an administrator disables a user
	 * or changes their group membership. Recorded only when its audit identifier was
	 * established earlier, for the same reason
	 * {@link #logSessionDestroyed(HttpSession, String)} guards against logging an unknown
	 * session credential.
	 * @param session the Spring Session being invalidated, or null if it could not be
	 * found
	 * @param reason the controlled invalidation reason
	 */
	public void logSessionDestroyed(Session session, String reason) {
		if (session == null) {
			return;
		}
		String auditSessionId = session.getAttribute(AUDIT_SESSION_ID_ATTRIBUTE);
		if (auditSessionId != null) {
			log("destroy_session", "end", auditSessionId, reason);
		}
	}

	private String auditSessionId(HttpSession session) {
		Object value = session.getAttribute(AUDIT_SESSION_ID_ATTRIBUTE);
		return (value instanceof String auditSessionId) ? auditSessionId : null;
	}

	private void log(String action, String type, String auditSessionId, String reason) {
		var event = LOGGER.atInfo()
			.addKeyValue("event.category", List.of("authentication"))
			.addKeyValue("event.type", List.of(type))
			.addKeyValue("event.action", action)
			.addKeyValue("event.outcome", "success")
			.addKeyValue("session.id", auditSessionId);
		if (reason != null) {
			event.addKeyValue("session.termination_reason", reason);
		}
		event.log("Session lifecycle event");
	}

}
