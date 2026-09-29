package com.example.commons.accounts.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.example.commons.accounts.validation.ResourceName;
import com.example.commons.accounts.validation.Username;

@Entity
@Table(name = "app_user")
public class AppUser extends AbstractAuditableEntity {

	@Username
	private String username;

	@ResourceName
	private String name;

	@Email
	@Size(max = 254)
	private String email;

	private boolean enabled;

	@NotEmpty
	@ManyToMany
	@JoinTable(name = "app_user_group", joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "group_id"))
	private Set<AppGroup> groups = new HashSet<>();

	protected AppUser() {
	}

	public AppUser(String username, String name, String email, boolean enabled) {
		this.username = username;
		this.name = name;
		this.email = email;
		this.enabled = enabled;
	}

	public String getUsername() {
		return this.username;
	}

	public String getName() {
		return this.name;
	}

	public String getEmail() {
		return this.email;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public Set<AppGroup> getGroups() {
		return this.groups;
	}

	public void update(String name, String email, boolean enabled) {
		this.name = name;
		this.email = email;
		this.enabled = enabled;
		touch();
	}

}
