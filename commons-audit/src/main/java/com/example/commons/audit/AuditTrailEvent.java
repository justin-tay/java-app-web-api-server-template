package com.example.commons.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the audit trail. It is append-only: it has a constructor and getters but no
 * way to change it, and no foreign key to what it is about, so it outlives a removed
 * account. Like every entity it has a sequence key for the database and a random public
 * ID for the API and the logs (see docs/adr/0036).
 *
 * <p>
 * It is the storage behind {@link AuditTrail}; callers read the trail through
 * {@link AuditRecord}.
 */
@Entity
@Table(name = "audit_event")
public class AuditTrailEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_event_seq")
	@SequenceGenerator(name = "audit_event_seq", sequenceName = "audit_event_seq", allocationSize = 50)
	private Long id;

	@Column(updatable = false)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID publicId = UUID.randomUUID();

	private Instant occurredAt;

	private String actor;

	private String action;

	@Enumerated(EnumType.STRING)
	private AuditOutcome outcome;

	private String targetType;

	private String targetId;

	private String targetName;

	private String targetFullName;

	private String reasonCode;

	private String reasonNote;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	private String details;

	protected AuditTrailEvent() {
	}

	AuditTrailEvent(Instant occurredAt, String actor, String action, AuditOutcome outcome, AuditTarget target,
			String reasonCode, String reasonNote, String details) {
		this.occurredAt = occurredAt;
		this.actor = actor;
		this.action = action;
		this.outcome = outcome;
		this.targetType = target.type();
		this.targetId = target.id();
		this.targetName = target.name();
		this.targetFullName = target.fullName();
		this.reasonCode = reasonCode;
		this.reasonNote = reasonNote;
		this.details = details;
	}

	public Long getId() {
		return this.id;
	}

	public UUID getPublicId() {
		return this.publicId;
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

	public AuditOutcome getOutcome() {
		return this.outcome;
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
