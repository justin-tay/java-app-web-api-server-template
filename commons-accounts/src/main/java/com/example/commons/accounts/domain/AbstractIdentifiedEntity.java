package com.example.commons.accounts.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Base entity with two identifiers (see docs/adr/0036). {@code id} is the primary key, a
 * number from a sequence that only the persistence layer and the services use, for joins
 * and foreign keys. {@code publicId} is the identifier the API, the audit trail and the
 * logs use. It is a random version 4 UUID, so it neither can be guessed nor discloses
 * when the entity was created, and it is known before the insert.
 */
@MappedSuperclass
public abstract class AbstractIdentifiedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;

	@Column(updatable = false)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID publicId = UUID.randomUUID();

	public Long getId() {
		return this.id;
	}

	public UUID getPublicId() {
		return this.publicId;
	}

}
