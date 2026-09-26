package com.example.commons.accounts.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.example.commons.accounts.validation.ResourceName;

/**
 * A role, granted to users through their groups. Its name is the authority the
 * application checks, prefixed with {@code ROLE_}, so it cannot be changed once the role
 * exists (see docs/adr/0022).
 */
@Entity
@Table(name = "app_role")
public class AppRole extends AbstractAuditableEntity {

	@ResourceName
	private String name;

	protected AppRole() {
	}

	public AppRole(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

}
