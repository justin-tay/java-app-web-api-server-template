package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One account in a confirmed population, as it was when the population was confirmed (see
 * docs/adr/0037). It has no foreign key to the account and no way to change.
 */
@Entity
@Table(name = "account_review_population_entry")
public class AccountReviewPopulationEntry extends AbstractIdentifiedEntity {

	private Long attestationId;

	@Column(name = "user_public_id")
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID userPublicId;

	private String username;

	private String fullName;

	private String department;

	private Instant lastLoginAt;

	private Instant lastActivityAt;

	private Instant occurredAt;

	private String actor;

	private String reasonCode;

	private String reasonNote;

	protected AccountReviewPopulationEntry() {
	}

	public AccountReviewPopulationEntry(Long attestationId, UUID userPublicId, String username, String fullName,
			String department, Instant lastLoginAt, Instant lastActivityAt, Instant occurredAt, String actor,
			String reasonCode, String reasonNote) {
		this.attestationId = attestationId;
		this.userPublicId = userPublicId;
		this.username = username;
		this.fullName = fullName;
		this.department = department;
		this.lastLoginAt = lastLoginAt;
		this.lastActivityAt = lastActivityAt;
		this.occurredAt = occurredAt;
		this.actor = actor;
		this.reasonCode = reasonCode;
		this.reasonNote = reasonNote;
	}

	public Long getAttestationId() {
		return this.attestationId;
	}

	public UUID getUserPublicId() {
		return this.userPublicId;
	}

	public String getUsername() {
		return this.username;
	}

	public String getFullName() {
		return this.fullName;
	}

	public String getDepartment() {
		return this.department;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public Instant getLastActivityAt() {
		return this.lastActivityAt;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getActor() {
		return this.actor;
	}

	public String getReasonCode() {
		return this.reasonCode;
	}

	public String getReasonNote() {
		return this.reasonNote;
	}

}
