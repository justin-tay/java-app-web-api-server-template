package com.example.commons.security.session;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.session.Session;

import com.example.commons.logging.LoggingContextKeys;

/**
 * Records session lifecycle events without ever recording the browser's session
 * credential. The audit identifier is a separate, random server-side session attribute.
 */
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

	/**
	 * Records a request that presented a session ID the session repository does not hold.
	 * The application cannot tell a session that ended (idle expiry, logout, revocation)
	 * from an ID it never issued, so the event says only that the requested session was
	 * not found. Neither the presented ID nor anything derived from it is logged.
	 */
	public void logRequestedSessionNotFound() {
		LOGGER.atInfo()
			.addKeyValue("event.category", List.of("authentication"))
			.addKeyValue("event.type", List.of("info"))
			.addKeyValue("event.action", "resume_session")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("event.reason", "session_not_found")
			.log("Requested session not found");
	}

	/**
	 * Records a change to the roles an authenticated session acts with, found when its
	 * {@code ROLE_} authorities are reloaded from the local user, group, and role model.
	 * Roles are logged by their stored names, without the {@code ROLE_} prefix, the same
	 * vocabulary the administration audit events use (see docs/adr/0021).
	 * @param session the session whose roles changed
	 * @param username the authenticated user
	 * @param added the role names granted since the session's previous roles
	 * @param removed the role names withdrawn since the session's previous roles
	 */
	public void logSessionPrivilegeChanged(HttpSession session, String username, Collection<String> added,
			Collection<String> removed) {
		logSessionCreatedIfNeeded(session);
		var event = LOGGER.atInfo()
			.addKeyValue("event.category", List.of("authentication"))
			.addKeyValue("event.type", List.of("info"))
			.addKeyValue("event.action", "update_session")
			.addKeyValue("event.outcome", "success")
			.addKeyValue("event.reason", "privilege_change")
			.addKeyValue("session.id", auditSessionId(session))
			.addKeyValue("roles.added", added.stream().sorted().toList())
			.addKeyValue("roles.removed", removed.stream().sorted().toList());
		if (MDC.get(LoggingContextKeys.USER_NAME) == null) {
			event.addKeyValue(LoggingContextKeys.USER_NAME, username);
		}
		event.log("Session privileges changed");
	}

	/**
	 * Records a change in the client IP a session's requests present, without
	 * invalidating it: unlike a User-Agent change, an IP change alone does not
	 * necessarily indicate hijacking (see docs/adr/0026). Recorded only when the audit
	 * identifier was established earlier, for the same reason
	 * {@link #logSessionDestroyed(HttpSession, String)} guards against logging an unknown
	 * session credential.
	 * @param session the session whose bound client IP changed
	 * @param previousClientIp the client IP the session was previously bound to
	 */
	public void logClientIpAnomaly(HttpSession session, String previousClientIp) {
		String auditSessionId = auditSessionId(session);
		if (auditSessionId == null) {
			return;
		}
		LOGGER.atInfo()
			.addKeyValue("event.category", List.of("authentication"))
			.addKeyValue("event.type", List.of("info"))
			.addKeyValue("event.action", "update_session")
			.addKeyValue("event.outcome", "success")
			.addKeyValue("event.reason", "client_ip_changed")
			.addKeyValue("session.id", auditSessionId)
			.addKeyValue("session.bound_client_ip", previousClientIp)
			.log("Session client IP changed");
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
