package com.example.commons.accounts.domain;

import java.time.Instant;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Base entity providing a random UUID identifier and creation/update audit timestamps and
 * actors, shared by every administration entity. The actor is the {@link Auditor}; these
 * columns record only the latest change, and the history of changes is the administration
 * audit log (see docs/adr/0021).
 */
@MappedSuperclass
public abstract class AbstractAuditableEntity {

	/**
	 * A UUID in its 36-character text form, held in the database's UUID type and
	 * generated as a random version 4 value when the entity is persisted, so the
	 * identifier does not disclose when the entity was created.
	 */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID id;

	private Instant createdAt;

	private Instant updatedAt;

	private String createdBy;

	private String updatedBy;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		this.createdBy = Auditor.current();
		this.updatedAt = this.createdAt;
		this.updatedBy = this.createdBy;
	}

	@PreUpdate
	void onUpdate() {
		touch();
	}

	public UUID getId() {
		return this.id;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public String getCreatedBy() {
		return this.createdBy;
	}

	public String getUpdatedBy() {
		return this.updatedBy;
	}

	/**
	 * Marks the entity as modified now by the current {@link Auditor}. JPA's
	 * {@link PreUpdate} callback already covers changes to mapped fields; this is for
	 * callers that need the change recorded immediately, such as after replacing a
	 * collection association, which does not make the entity itself dirty.
	 */
	public void touch() {
		this.updatedAt = Instant.now();
		this.updatedBy = Auditor.current();
	}

}
