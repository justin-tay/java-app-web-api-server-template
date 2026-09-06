package com.example.app.web.server.security;

import java.util.function.Supplier;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
		LOGGER.atInfo()
			.addKeyValue("event.category", "authentication")
			.addKeyValue("event.type", "info")
			.addKeyValue("event.action", "login")
			.addKeyValue("event.outcome", "success")
			.addKeyValue("user.name", event.getAuthentication().getName())
			.log("User authenticated");
	}

	@EventListener
	void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
		LOGGER.atWarn()
			.addKeyValue("event.category", "authentication")
			.addKeyValue("event.type", "denied")
			.addKeyValue("event.action", "login")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("user.name", event.getAuthentication().getName())
			.addKeyValue("error.type", event.getException().getClass().getSimpleName())
			.log("Authentication failed");
	}

	@EventListener
	void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
		LOGGER.atWarn()
			.addKeyValue("event.category", List.of("web", "api"))
			.addKeyValue("event.type", List.of("access", "denied"))
			.addKeyValue("event.action", "authorize_access")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("user.name", username(event.getAuthentication()))
			.log("Authorization denied");
	}

	@EventListener
	void onLogoutSuccess(LogoutSuccessEvent event) {
		LOGGER.atInfo()
			.addKeyValue("event.category", "authentication")
			.addKeyValue("event.type", "info")
			.addKeyValue("event.action", "logout")
			.addKeyValue("event.outcome", "success")
			.addKeyValue("user.name", event.getAuthentication().getName())
			.log("User logged out");
	}

	private String username(Supplier<Authentication> authenticationSupplier) {
		Authentication authentication = authenticationSupplier.get();
		return (authentication != null) ? authentication.getName() : "anonymous";
	}

}
