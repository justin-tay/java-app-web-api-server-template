package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Base entity providing a random UUID identifier and creation/update audit timestamps,
 * shared by every administration entity.
 */
@MappedSuperclass
public abstract class AbstractAuditableEntity {

	/**
	 * A UUID in its 36-character text form, stored as {@code CHAR(36)} as
	 * {@code 001-authorisation-schema.sql} defines it.
	 */
	@Id
	@Column(length = 36)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String id;

	private Instant createdAt;

	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		this.id = UUID.randomUUID().toString();
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public String getId() {
		return this.id;
	}

	/**
	 * Marks the entity as modified now. JPA's {@link PreUpdate} callback already covers
	 * changes to mapped fields; this is for callers that need the timestamp updated
	 * immediately, such as after replacing a collection association.
	 */
	public void touch() {
		this.updatedAt = Instant.now();
	}

}
