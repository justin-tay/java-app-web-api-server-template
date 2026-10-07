package com.example.commons.audit;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * One event read back from the audit trail. Its details are read as the type their writer
 * recorded, so the writer's record is the contract and a renamed key fails to compile
 * rather than reading as null. Keys a type does not declare are ignored, so an older row
 * still reads.
 */
public final class AuditRecord {

	static final JsonMapper JSON = JsonMapper.builder()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.build();

	private final AuditTrailEvent row;

	AuditRecord(AuditTrailEvent row) {
		this.row = row;
	}

	/**
	 * Returns the event's public ID, which its log event carries as {@code event.id}.
	 * @return the ID
	 */
	public UUID id() {
		return this.row.getPublicId();
	}

	public Instant occurredAt() {
		return this.row.getOccurredAt();
	}

	public String actor() {
		return this.row.getActor();
	}

	public String action() {
		return this.row.getAction();
	}

	public AuditOutcome outcome() {
		return this.row.getOutcome();
	}

	public AuditTarget target() {
		return new AuditTarget(this.row.getTargetType(), this.row.getTargetId(), this.row.getTargetName(),
				this.row.getTargetFullName(), false);
	}

	public String reasonCode() {
		return this.row.getReasonCode();
	}

	public String reasonNote() {
		return this.row.getReasonNote();
	}

	/**
	 * Returns the details as the given type: the writer's record, or {@code Object} for a
	 * map.
	 * @param <T> the type
	 * @param type the type
	 * @return the details, or null if the event has none
	 */
	public <T> T details(Class<T> type) {
		return (this.row.getDetails() != null) ? JSON.readValue(this.row.getDetails(), type) : null;
	}

}
