package com.example.app.web.server.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Base entity providing a random UUID identifier and creation/update audit timestamps,
 * shared by every administration entity.
 */
@MappedSuperclass
public abstract class AbstractAuditableEntity {

	@Id
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
