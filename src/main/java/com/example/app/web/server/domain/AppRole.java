package com.example.app.web.server.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.example.app.web.server.validation.ResourceName;

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

	public void setName(String name) {
		this.name = name;
		touch();
	}

}
