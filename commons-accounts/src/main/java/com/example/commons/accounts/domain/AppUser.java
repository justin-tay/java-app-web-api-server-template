package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Size(max = 100)
	private String department;

	@Enumerated(EnumType.STRING)
	private AccountStatus status = AccountStatus.ACTIVE;

	private Instant suspendedAt;

	private String suspensionReasonCode;

	private String suspensionNote;

	private Instant inactivityClockStartedAt;

	private Instant lastLoginAt;

	@NotEmpty
	@ManyToMany
	@JoinTable(name = "app_user_group", joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "group_id"))
	private Set<AppGroup> groups = new HashSet<>();

	protected AppUser() {
	}

	/**
	 * Creates an active account whose inactivity clock starts now.
	 */
	public AppUser(String username, String name, String email) {
		this(username, name, email, null);
	}

	public AppUser(String username, String name, String email, String department) {
		this.username = username;
		this.name = name;
		this.email = email;
		this.department = department;
		this.inactivityClockStartedAt = Instant.now();
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

	public String getDepartment() {
		return this.department;
	}

	public AccountStatus getStatus() {
		return this.status;
	}

	public boolean isSuspended() {
		return this.status == AccountStatus.SUSPENDED;
	}

	public Instant getSuspendedAt() {
		return this.suspendedAt;
	}

	public String getSuspensionReasonCode() {
		return this.suspensionReasonCode;
	}

	public String getSuspensionNote() {
		return this.suspensionNote;
	}

	public Instant getInactivityClockStartedAt() {
		return this.inactivityClockStartedAt;
	}

	/**
	 * Returns when the account was last in use: the later of its last sign-in and the
	 * time it was created or last unsuspended. This is what the inactivity thresholds
	 * count from; {@code lastLoginAt} itself is only ever set by a sign-in.
	 */
	public Instant lastActivityAt() {
		if (this.lastLoginAt != null && this.lastLoginAt.isAfter(this.inactivityClockStartedAt)) {
			return this.lastLoginAt;
		}
		return this.inactivityClockStartedAt;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public Set<AppGroup> getGroups() {
		return this.groups;
	}

	public void update(String name, String email, String department) {
		this.name = name;
		this.email = email;
		this.department = department;
		touch();
	}

	/**
	 * Suspends the account, recording when and why.
	 */
	public void suspend(Instant at, ReasonCode reason, String note) {
		this.status = AccountStatus.SUSPENDED;
		this.suspendedAt = at;
		this.suspensionReasonCode = reason.value();
		this.suspensionNote = note;
		touch();
	}

	/**
	 * Makes the account active again and restarts its inactivity clock, without touching
	 * {@code lastLoginAt}, which is only set by a sign-in.
	 */
	public void unsuspend(Instant at) {
		this.status = AccountStatus.ACTIVE;
		this.suspendedAt = null;
		this.suspensionReasonCode = null;
		this.suspensionNote = null;
		this.inactivityClockStartedAt = at;
		touch();
	}

}
