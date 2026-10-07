package com.example.commons.accounts.domain;

import java.time.Instant;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import com.example.commons.audit.Auditor;

/**
 * Base entity providing the identifiers of {@link AbstractIdentifiedEntity} and
 * creation/update audit timestamps and actors, shared by every administration entity. The
 * actor is the {@link Auditor}; these columns record only the latest change, and the
 * history of changes is the administration audit log (see docs/adr/0021).
 */
@MappedSuperclass
public abstract class AbstractAuditableEntity extends AbstractIdentifiedEntity {

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
