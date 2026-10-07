package com.example.commons.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One event to record in the audit trail: an action on a target, with an optional reason,
 * the details kept in the row, and the fields added to the log event. Built with
 * {@link #of(AuditAction, AuditTarget)} and immutable once built.
 *
 * <p>
 * The details, the target's full name and the reason note go to the row only, and only
 * the {@linkplain Builder#log(String, Object) log fields} reach the log event, so
 * personal data stays out of the logs by construction. A log field may not use a key the
 * audit trail writes itself ({@code event.*}, {@code user.name}, {@code related.user}) or
 * one that names personal data ({@code *email}, {@code *full_name}, {@code *note}).
 */
public final class AuditEvent {

	private final AuditAction action;

	private final AuditTarget target;

	private final String reasonCode;

	private final String reasonNote;

	private final Object details;

	private final Map<String, Object> logFields;

	private final Set<String> relatedUsers;

	private AuditEvent(Builder builder) {
		this.action = builder.action;
		this.target = builder.target;
		this.reasonCode = builder.reasonCode;
		this.reasonNote = builder.reasonNote;
		this.details = builder.details;
		this.logFields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.logFields));
		this.relatedUsers = Collections.unmodifiableSet(new TreeSet<>(builder.relatedUsers));
	}

	/**
	 * Starts an event.
	 * @param action the action
	 * @param target the target
	 * @return the builder
	 */
	public static Builder of(AuditAction action, AuditTarget target) {
		return new Builder(action, target);
	}

	AuditAction action() {
		return this.action;
	}

	AuditTarget target() {
		return this.target;
	}

	String reasonCode() {
		return this.reasonCode;
	}

	String reasonNote() {
		return this.reasonNote;
	}

	Object details() {
		return this.details;
	}

	Map<String, Object> logFields() {
		return this.logFields;
	}

	Set<String> relatedUsers() {
		return this.relatedUsers;
	}

	/**
	 * Builds an {@link AuditEvent}.
	 */
	public static final class Builder {

		private final AuditAction action;

		private final AuditTarget target;

		private String reasonCode;

		private String reasonNote;

		private Object details;

		private final Map<String, Object> logFields = new LinkedHashMap<>();

		private final Set<String> relatedUsers = new TreeSet<>();

		private Builder(AuditAction action, AuditTarget target) {
			if (action == null || target == null) {
				throw new IllegalArgumentException("An audit event needs an action and a target.");
			}
			this.action = action;
			this.target = target;
		}

		/**
		 * Sets the controlled reason, logged as {@code event.reason}.
		 * @param code the reason code, such as {@code left_organisation}, or null
		 * @return this builder
		 */
		public Builder reason(String code) {
			this.reasonCode = code;
			return this;
		}

		/**
		 * Sets the controlled reason and the free-text note, which is kept in the row and
		 * never logged.
		 * @param code the reason code, or null
		 * @param note the note, or null
		 * @return this builder
		 */
		public Builder reason(String code, String note) {
			this.reasonCode = code;
			this.reasonNote = note;
			return this;
		}

		/**
		 * Sets the details kept in the row as JSON and never logged. A record per action
		 * is the contract between the writer and whoever reads the details back with
		 * {@link AuditRecord#details(Class)}.
		 * @param details the details, or null
		 * @return this builder
		 */
		public Builder details(Object details) {
			this.details = details;
			return this;
		}

		/**
		 * Adds a field to the log event only, named as ECS names it.
		 * @param key the field name, such as {@code user.target.status}
		 * @param value the value
		 * @return this builder
		 * @throws IllegalArgumentException if the key is one the audit trail writes
		 * itself or names personal data
		 */
		public Builder log(String key, Object value) {
			String lower = key.toLowerCase(Locale.ROOT);
			if (lower.startsWith("event.") || lower.equals("user.name") || lower.equals("related.user")) {
				throw new IllegalArgumentException("The audit trail writes " + key + " itself.");
			}
			if (lower.endsWith("email") || lower.endsWith("full_name") || lower.endsWith("note")) {
				throw new IllegalArgumentException(key + " would log personal data.");
			}
			this.logFields.put(key, value);
			return this;
		}

		/**
		 * Adds a user the event concerns, other than the actor and a user target, to
		 * {@code related.user}.
		 * @param username the username
		 * @return this builder
		 */
		public Builder relatedUser(String username) {
			if (username != null) {
				this.relatedUsers.add(username);
			}
			return this;
		}

		public AuditEvent build() {
			return new AuditEvent(this);
		}

	}

}
