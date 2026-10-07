package com.example.commons.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Exact criteria for reading events back from the audit trail, for a caller that reads
 * what it wrote. Every criterion set narrows the result. Only successful events match
 * unless {@link #anyOutcome()} is set, so a refused removal is never read as a removal.
 */
public final class AuditQuery {

	private List<String> actions;

	private String targetType;

	private List<String> targetIds;

	private List<UUID> ids;

	private Instant since;

	private boolean anyOutcome;

	private AuditQuery() {
	}

	/**
	 * Starts a query that matches every successful event.
	 * @return the query
	 */
	public static AuditQuery where() {
		return new AuditQuery();
	}

	/**
	 * Matches the events of one of the actions.
	 * @param actions the action names
	 * @return this query
	 */
	public AuditQuery action(String... actions) {
		this.actions = List.of(actions);
		return this;
	}

	public AuditQuery targetType(String targetType) {
		this.targetType = targetType;
		return this;
	}

	/**
	 * Matches the events about one of the targets.
	 * @param targetIds the target IDs
	 * @return this query
	 */
	public AuditQuery targetIds(Collection<String> targetIds) {
		this.targetIds = List.copyOf(targetIds);
		return this;
	}

	/**
	 * Matches the events with one of the public IDs.
	 * @param ids the event IDs
	 * @return this query
	 */
	public AuditQuery ids(Collection<UUID> ids) {
		this.ids = List.copyOf(ids);
		return this;
	}

	/**
	 * Matches the events that occurred at or after an instant.
	 * @param since the instant, or null for no lower bound
	 * @return this query
	 */
	public AuditQuery since(Instant since) {
		this.since = since;
		return this;
	}

	/**
	 * Matches refused attempts as well as successful events.
	 * @return this query
	 */
	public AuditQuery anyOutcome() {
		this.anyOutcome = true;
		return this;
	}

	List<String> actions() {
		return this.actions;
	}

	String targetType() {
		return this.targetType;
	}

	List<String> targetIds() {
		return this.targetIds;
	}

	List<UUID> ids() {
		return this.ids;
	}

	Instant since() {
		return this.since;
	}

	boolean isAnyOutcome() {
		return this.anyOutcome;
	}

}
