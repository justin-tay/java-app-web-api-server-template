package com.example.app.web.server.domain;

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

import com.example.app.web.server.validation.DisplayName;
import com.example.app.web.server.validation.Username;

@Entity
@Table(name = "app_user")
public class AppUser extends AbstractAuditableEntity {

	@Username
	private String username;

	@DisplayName
	private String displayName;

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

	public AppUser(String username, String displayName, String email, boolean enabled) {
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.enabled = enabled;
	}

	public String getUsername() {
		return this.username;
	}

	public String getDisplayName() {
		return this.displayName;
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

	public void update(String displayName, String email, boolean enabled) {
		this.displayName = displayName;
		this.email = email;
		this.enabled = enabled;
		touch();
	}

}
