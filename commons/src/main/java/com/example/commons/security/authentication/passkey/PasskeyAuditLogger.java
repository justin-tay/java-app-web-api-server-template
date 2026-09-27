package com.example.commons.security.authentication.passkey;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

import com.example.commons.logging.LoggingContextKeys;

/**
 * Records passkey registration and removal, and a passkey login refused because its
 * signature counter went backwards, with the field vocabulary of the administration audit
 * events (see docs/adr/0021 and docs/adr/0024). Login success and failure are recorded by
 * {@code SecurityAuditEventLogger}, like every other login. No credential material is
 * logged, only the owner and the label the user gave the passkey.
 */
public class PasskeyAuditLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(PasskeyAuditLogger.class);

	/**
	 * Records a passkey a user registered.
	 * @param owner the username the passkey belongs to
	 * @param label the label the user gave the passkey
	 */
	public void registered(String owner, String label) {
		event(LOGGER.atInfo(), "register_passkey", "creation", owner).addKeyValue("event.outcome", "success")
			.addKeyValue("passkey.label", label)
			.log("Passkey registered");
	}

	/**
	 * Records a passkey that was removed, by its owner or by an administrator.
	 * @param owner the username the passkey belonged to
	 * @param label the label the user had given the passkey
	 */
	public void removed(String owner, String label) {
		event(LOGGER.atInfo(), "remove_passkey", "deletion", owner).addKeyValue("event.outcome", "success")
			.addKeyValue("passkey.label", label)
			.log("Passkey removed");
	}

	/**
	 * Records a passkey login refused because the authenticator's signature counter did
	 * not increase, which can mean the passkey was cloned.
	 * @param owner the username the passkey belongs to
	 * @param label the label the user gave the passkey
	 */
	public void counterRegression(String owner, String label) {
		LOGGER.atWarn()
			.addKeyValue("event.category", List.of("authentication"))
			.addKeyValue("event.type", List.of("denied"))
			.addKeyValue("event.action", "login")
			.addKeyValue("event.outcome", "failure")
			.addKeyValue("event.reason", "passkey_signature_counter_regression")
			.addKeyValue("user.target.name", owner)
			.addKeyValue("passkey.label", label)
			.log("Passkey signature counter regression");
	}

	private static LoggingEventBuilder event(LoggingEventBuilder event, String action, String type, String owner) {
		event.addKeyValue("event.category", List.of("iam"))
			.addKeyValue("event.type", List.of("passkey", type))
			.addKeyValue("event.action", action)
			.addKeyValue("user.target.name", owner);
		String actor = MDC.get(LoggingContextKeys.USER_NAME);
		SortedSet<String> related = new TreeSet<>();
		related.add(owner);
		if (actor != null) {
			related.add(actor);
		}
		return event.addKeyValue("related.user", List.copyOf(related));
	}

}
