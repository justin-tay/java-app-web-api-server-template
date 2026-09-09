package com.example.app.web.server.security;

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
import org.springframework.stereotype.Component;

/**
 * Records security-relevant application events without including credentials, tokens,
 * cookies, or request parameters.
 */
@Component
class SecurityAuditEventLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditEventLogger.class);

	@EventListener
	void onAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
		updateLoggingContextUser(event.getAuthentication());
		LOGGER.atInfo()
			.addKeyValue("event.category", "authentication")
			.addKeyValue("event.type", List.of("info"))
			.addKeyValue("event.action", "login")
			.addKeyValue("event.outcome", "success")
			.log("User authenticated");
	}

	@EventListener
	void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
		LOGGER.atWarn()
			.addKeyValue("event.category", "authentication")
			.addKeyValue("event.type", List.of("denied"))
			.addKeyValue("event.action", "login")
			.addKeyValue("event.outcome", "failure")
			// The attempted account is the target of the failed action, not an
			// authenticated actor.
			// See https://github.com/elastic/integrations/issues/20105.
			.addKeyValue("user.target.name", event.getAuthentication().getName())
			.addKeyValue("error.type", event.getException().getClass().getSimpleName())
			.log("Authentication failed");
	}

	@EventListener
	void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
		Authentication authentication = event.getAuthentication().get();
		LoggingEventBuilder logEvent = LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web", "api"))
			.addKeyValue("event.type", List.of("access", "denied"))
			.addKeyValue("event.action", "authorize_access")
			.addKeyValue("event.outcome", "failure");
		if (authentication == null) {
			logEvent.addKeyValue("user.name", "anonymous");
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
			.addKeyValue("event.category", "authentication")
			.addKeyValue("event.type", List.of("info"))
			.addKeyValue("event.action", "logout")
			.addKeyValue("event.outcome", "success")
			.log("User logged out");
	}

	private void updateLoggingContextUser(Authentication authentication) {
		MDC.put("user.name", authentication.getName());
	}

}
