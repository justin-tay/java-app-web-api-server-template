package com.example.app.web.server.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.example.app.web.server.validation.ResourceName;

@Entity
@Table(name = "app_group")
public class AppGroup {

	@Id
	private String id;

	@ResourceName
	private String name;

	@ManyToMany
	@JoinTable(name = "app_group_role", joinColumns = @JoinColumn(name = "group_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<AppRole> roles = new HashSet<>();

	private Instant createdAt;

	private Instant updatedAt;

	protected AppGroup() {
	}

	public AppGroup(String name) {
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

	public void touch() {
		this.updatedAt = Instant.now();
	}

	public Set<AppRole> getRoles() {
		return this.roles;
	}

}
