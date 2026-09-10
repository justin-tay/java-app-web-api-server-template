package com.example.app.web.server.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.example.app.web.server.validation.ResourceName;

@Entity
@Table(name = "app_group")
public class AppGroup extends AbstractAuditableEntity {

	@ResourceName
	private String name;

	@ManyToMany
	@JoinTable(name = "app_group_role", joinColumns = @JoinColumn(name = "group_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<AppRole> roles = new HashSet<>();

	protected AppGroup() {
	}

	public AppGroup(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
		touch();
	}

	public Set<AppRole> getRoles() {
		return this.roles;
	}

}
