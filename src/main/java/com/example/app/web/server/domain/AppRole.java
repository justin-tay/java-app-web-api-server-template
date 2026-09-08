package com.example.app.web.server.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.example.app.web.server.validation.ResourceName;

@Entity
@Table(name = "app_role")
public class AppRole {

	@Id
	private String id;

	@ResourceName
	private String name;

	private Instant createdAt;

	private Instant updatedAt;

	protected AppRole() {
	}

	public AppRole(String name) {
		this.name = name;
	}

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

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
		this.updatedAt = Instant.now();
	}

}
