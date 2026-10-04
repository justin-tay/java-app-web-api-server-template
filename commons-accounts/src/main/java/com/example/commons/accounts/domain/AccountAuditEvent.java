package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One row of the business audit trail (see docs/adr/0030). It is append-only: it has a
 * constructor and getters but no way to change it, and it has no foreign key to the
 * account it is about, so it outlives a removed account.
 */
@Entity
@Table(name = "account_audit_event")
public class AccountAuditEvent {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID id;

	private Instant occurredAt;

	private String actor;

	private String action;

	private String targetType;

	private String targetId;

	private String targetName;

	private String targetFullName;

	private String reasonCode;

	private String reasonNote;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private String details;

	protected AccountAuditEvent() {
	}

	public AccountAuditEvent(Instant occurredAt, String actor, String action, String targetType, String targetId,
			String targetName, String targetFullName, String reasonCode, String reasonNote, String details) {
		this.occurredAt = occurredAt;
		this.actor = actor;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.targetName = targetName;
		this.targetFullName = targetFullName;
		this.reasonCode = reasonCode;
		this.reasonNote = reasonNote;
		this.details = details;
	}

	public UUID getId() {
		return this.id;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getActor() {
		return this.actor;
	}

	public String getAction() {
		return this.action;
	}

	public String getTargetType() {
		return this.targetType;
	}

	public String getTargetId() {
		return this.targetId;
	}

	public String getTargetName() {
		return this.targetName;
	}

	public String getTargetFullName() {
		return this.targetFullName;
	}

	public String getReasonCode() {
		return this.reasonCode;
	}

	public String getReasonNote() {
		return this.reasonNote;
	}

	public String getDetails() {
		return this.details;
	}

}
