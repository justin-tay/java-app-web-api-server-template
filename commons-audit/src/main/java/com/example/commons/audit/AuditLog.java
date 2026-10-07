package com.example.commons.audit;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

import com.example.commons.logging.LoggingContextKeys;

/**
 * Writes the ECS log event of an audit trail row: the action's {@code event.category} and
 * {@code event.type}, {@code event.action}, {@code event.outcome}, {@code event.reason},
 * the row's public ID and time as {@code event.id} and {@code event.created}, the actor
 * as {@code user.name} when the request has not already put it in the MDC,
 * {@code related.user}, and the event's own log fields. A success is logged at
 * {@code INFO} and a refusal at {@code WARN}.
 */
final class AuditLog {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuditTrail.class);

	private AuditLog() {
	}

	static void write(AuditEvent event, AuditTrailEvent row) {
		boolean success = row.getOutcome() == AuditOutcome.SUCCESS;
		LoggingEventBuilder log = (success ? LOGGER.atInfo() : LOGGER.atWarn())
			.addKeyValue("event.category", event.action().category())
			.addKeyValue("event.type", event.action().type())
			.addKeyValue("event.action", event.action().name())
			.addKeyValue("event.outcome", row.getOutcome().value())
			.addKeyValue("event.id", row.getPublicId().toString())
			.addKeyValue("event.created", row.getOccurredAt().toString());
		if (row.getReasonCode() != null) {
			log.addKeyValue("event.reason", row.getReasonCode());
		}
		boolean authenticated = !Auditor.SYSTEM.equals(row.getActor());
		if (authenticated && MDC.get(LoggingContextKeys.USER_NAME) == null) {
			log.addKeyValue(LoggingContextKeys.USER_NAME, row.getActor());
		}
		SortedSet<String> related = new TreeSet<>(event.relatedUsers());
		if (authenticated) {
			related.add(row.getActor());
		}
		if (event.target().user()) {
			related.add(event.target().name());
		}
		if (!related.isEmpty()) {
			log.addKeyValue("related.user", List.copyOf(related));
		}
		event.logFields().forEach(log::addKeyValue);
		log.log(success ? event.action().message() : event.action().message() + " rejected");
	}

	/**
	 * Logs that a refusal's row could not be written. Its {@code WARN} event, already
	 * logged with the same {@code event.id}, remains the record.
	 */
	static void rowNotWritten(AuditTrailEvent row, RuntimeException failure) {
		LOGGER.atError()
			.addKeyValue("event.action", row.getAction())
			.addKeyValue("event.id", row.getPublicId().toString())
			.setCause(failure)
			.log("Audit trail row of a refusal could not be written");
	}

}
