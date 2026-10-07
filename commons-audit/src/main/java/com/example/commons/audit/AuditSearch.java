package com.example.commons.audit;

import java.time.Instant;

/**
 * Filters for listing the audit trail to a person. Every non-null value narrows the
 * result: the actor, target name and action match a value containing the text, ignoring
 * case, and the target type and outcome match exactly. Refused attempts are listed unless
 * an outcome is given.
 *
 * @param actor text the actor contains
 * @param targetType the exact target type
 * @param targetName text the target name contains
 * @param action text the action contains
 * @param outcome the exact outcome
 * @param from the earliest time, inclusive
 * @param to the latest time, exclusive
 */
public record AuditSearch(String actor, String targetType, String targetName, String action, AuditOutcome outcome,
		Instant from, Instant to) {

}
