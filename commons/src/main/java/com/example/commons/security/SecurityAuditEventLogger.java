package com.example.commons.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.commons.logging.EcsFields;
import com.example.commons.security.session.SessionLifecycleAuditLogger;

/**
 * Records security-relevant application events without including credentials, tokens,
 * cookies, or request parameters.
 */
public class SecurityAuditEventLogger {

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditEventLogger.class);

	public SecurityAuditEventLogger(SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@EventListener
	void onAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
		updateLoggingContextUser(event.getAuthentication());
		LOGGER.atInfo()
			.addKeyValue(EcsFields.EVENT_CATEGORY, List.of("authentication"))
			.addKeyValue(EcsFields.EVENT_TYPE, List.of("info"))
			.addKeyValue(EcsFields.EVENT_ACTION, "login")
			.addKeyValue(EcsFields.EVENT_OUTCOME, "success")
			.log("User authenticated");
	}

	@EventListener
	void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
		LOGGER.atWarn()
			.addKeyValue(EcsFields.EVENT_CATEGORY, List.of("authentication"))
			.addKeyValue(EcsFields.EVENT_TYPE, List.of("denied"))
			.addKeyValue(EcsFields.EVENT_ACTION, "login")
			.addKeyValue(EcsFields.EVENT_OUTCOME, "failure")
			// The attempted account is the target of the failed action, not an
			// authenticated actor.
			// See https://github.com/elastic/integrations/issues/20105.
			.addKeyValue("user.target.name", event.getAuthentication().getName())
			// setCause(event.getException()) is deliberately not used here: a failed
			// login attempt is expected, not a bug, so a stack trace is noise, and the
			// exception message can echo submitted credentials/username content; see
			// docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md,
			// "Sensitive-data policy".
			.addKeyValue("error.type", event.getException().getClass().getSimpleName())
			.log("Authentication failed");
	}

	@EventListener
	void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
		Authentication authentication = event.getAuthentication().get();
		LoggingEventBuilder logEvent = LOGGER.atWarn()
			.addKeyValue(EcsFields.EVENT_CATEGORY, List.of("web", "api"))
			.addKeyValue(EcsFields.EVENT_TYPE, List.of("access", "denied"))
			.addKeyValue(EcsFields.EVENT_ACTION, "authorize_access")
			.addKeyValue(EcsFields.EVENT_OUTCOME, "failure");
		if (authentication == null) {
			logEvent.addKeyValue(EcsFields.USER_NAME, "anonymous");
		}
		else {
			updateLoggingContextUser(authentication);
		}
		logEvent.log("Authorization denied");
	}

	@EventListener
	void onLogoutSuccess(LogoutSuccessEvent event) {
		updateLoggingContextUser(event.getAuthentication());
		LOGGER.atInfo()
			.addKeyValue(EcsFields.EVENT_CATEGORY, List.of("authentication"))
			.addKeyValue(EcsFields.EVENT_TYPE, List.of("info"))
			.addKeyValue(EcsFields.EVENT_ACTION, "logout")
			.addKeyValue(EcsFields.EVENT_OUTCOME, "success")
			.log("User logged out");
	}

	@EventListener
	void onSessionFixationProtection(SessionFixationProtectionEvent event) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
				&& attributes.getRequest().getSession(false) != null) {
			this.sessionLifecycleAuditLogger.logSessionRenewed(event, attributes.getRequest().getSession(false));
		}
	}

	/**
	 * Deliberately not scoped: the user must stay in the MDC until the request completes
	 * so the request's final log event carries it. LoggingContextCleanupFilter clears it.
	 */
	private void updateLoggingContextUser(Authentication authentication) {
		MDC.put(EcsFields.USER_NAME, authentication.getName());
	}

}
