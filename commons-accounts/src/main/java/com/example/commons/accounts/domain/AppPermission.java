package com.example.commons.accounts.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Something a user may do, named by a domain and an action, such as {@code user} and
 * {@code create}. Its authority is {@code domain:action}, which the code checks with
 * {@code hasAuthority()}. Permissions are reference data seeded by the schema and are not
 * changed through the API (see docs/adr/0038).
 */
@Entity
@Table(name = "app_permission")
public class AppPermission extends AbstractIdentifiedEntity {

	private String domain;

	private String action;

	/**
	 * Whether holding the permission makes an account privileged, because it grants
	 * access or changes the settings the application runs under.
	 */
	private boolean privileged;

	/**
	 * The permissions that no user may hold together with this one. A pair is stored
	 * once, so use {@link #conflictsWith(AppPermission)} to ask about either direction.
	 */
	@ManyToMany
	@JoinTable(name = "app_permission_conflict", joinColumns = @JoinColumn(name = "permission_id"),
			inverseJoinColumns = @JoinColumn(name = "conflicting_permission_id"))
	private Set<AppPermission> conflicting = new HashSet<>();

	protected AppPermission() {
	}

	public AppPermission(String domain, String action, boolean privileged) {
		this.domain = domain;
		this.action = action;
		this.privileged = privileged;
	}

	public String getDomain() {
		return this.domain;
	}

	public String getAction() {
		return this.action;
	}

	public boolean isPrivileged() {
		return this.privileged;
	}

	/**
	 * Returns the permission's authority, {@code domain:action}.
	 * @return the authority
	 */
	public String getName() {
		return this.domain + ":" + this.action;
	}

	public Set<AppPermission> getConflicting() {
		return this.conflicting;
	}

	/**
	 * Returns whether no user may hold this permission together with the other, whichever
	 * of the two lists the other.
	 * @param other the other permission
	 * @return whether they conflict
	 */
	public boolean conflictsWith(AppPermission other) {
		return this.conflicting.contains(other) || other.conflicting.contains(this);
	}

}
