package com.example.commons.accounts.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.example.commons.accounts.validation.ResourceName;

/**
 * A named set of permissions that a user can hold. The name is only a label, so it can
 * change; what a role grants is its permissions (see docs/adr/0038).
 */
@Entity
@Table(name = "app_role")
public class AppRole extends AbstractAuditableEntity {

	@ResourceName
	private String name;

	@ManyToMany
	@JoinTable(name = "app_role_permission", joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<AppPermission> permissions = new HashSet<>();

	protected AppRole() {
	}

	public AppRole(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
		touch();
	}

	public Set<AppPermission> getPermissions() {
		return this.permissions;
	}

}
