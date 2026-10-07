package com.example.commons.accounts.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.audit.AuditAction;
import com.example.commons.audit.AuditEvent;
import com.example.commons.audit.AuditQuery;
import com.example.commons.audit.AuditRecord;
import com.example.commons.audit.AuditTarget;
import com.example.commons.audit.AuditTrail;

/**
 * Records every change to the local accounts and roles in the {@link AuditTrail}, and
 * reads back what the account review needs from it (see docs/adr/0040). It owns the
 * account vocabulary of the trail: the actions, the details each one keeps, and the ECS
 * log fields, so that who granted or withdrew which access, and when, can be
 * reconstructed from the logs alone (see docs/adr/0021).
 *
 * <p>
 * Following ECS's user field usage, {@code user.target.*} and {@code role.*} hold an
 * object's state before the change, and {@code *.changes.*} hold only the values that
 * changed, so every event is self-contained. Permission values are {@code domain:action},
 * and {@code roles.added}, {@code roles.removed}, {@code permissions.added}, and
 * {@code permissions.removed} list the access each change grants or withdraws. The values
 * of personal-data fields are never logged, only the names of the ones that changed.
 *
 * <p>
 * A refused change is recorded under the action it attempted, and each {@code *Rejected}
 * method returns the exception for the caller to throw.
 */
public class AccountAudit {

	static final AuditAction CREATE_USER = AuditAction.iam("create_user", "user", "creation", "User creation");

	static final AuditAction UPDATE_USER = AuditAction.iam("update_user", "user", "change", "User update");

	static final AuditAction SUSPEND_USER = AuditAction.iam("suspend_user", "user", "change", "User suspension");

	static final AuditAction UNSUSPEND_USER = AuditAction.iam("unsuspend_user", "user", "change", "User unsuspension");

	static final AuditAction DELETE_USER = AuditAction.iam("delete_user", "user", "deletion", "User deletion");

	static final AuditAction REVOKE_SESSIONS = AuditAction.iam("revoke_sessions", "user", "change",
			"Session revocation");

	static final AuditAction CREATE_ROLE = AuditAction.iam("create_role", "group", "creation", "Role creation");

	static final AuditAction UPDATE_ROLE = AuditAction.iam("update_role", "group", "change", "Role update");

	static final AuditAction DELETE_ROLE = AuditAction.iam("delete_role", "group", "deletion", "Role deletion");

	private static final String USER = "USER";

	private static final String ROLE = "ROLE";

	private final AuditTrail trail;

	public AccountAudit(AuditTrail trail) {
		this.trail = trail;
	}

	/**
	 * A user's security-relevant state.
	 *
	 * @param id the user ID
	 * @param username the username
	 * @param status the account status, {@code active} or {@code suspended}
	 * @param roles the names of the user's roles
	 * @param permissions the permissions the user's roles grant, as {@code domain:action}
	 * @param privileged whether any of the permissions is privileged
	 * @param email the email address, compared but never recorded
	 * @param name the name, kept as the target's full name and never logged
	 * @param department the department, kept in a removal's details but never logged
	 * @param lastLoginAt the last sign-in time, kept in a removal's details
	 * @param lastActivityAt when the account was last in use, kept in a removal's details
	 * @param createdAt when the account was created, kept in a removal's details
	 */
	public record UserState(UUID id, String username, String status, SortedSet<String> roles,
			SortedSet<String> permissions, boolean privileged, String email, String name, String department,
			Instant lastLoginAt, Instant lastActivityAt, Instant createdAt) {

		public static UserState of(AppUser user) {
			return new UserState(user.getPublicId(), user.getUsername(),
					user.getStatus().name().toLowerCase(Locale.ROOT),
					names(user.getRoles().stream().map(AppRole::getName).toList()),
					names(user.permissions().stream().map(AppPermission::getName).toList()), user.isPrivileged(),
					user.getEmail(), user.getName(), user.getDepartment(), user.getLastLoginAt(), user.lastActivityAt(),
					user.getCreatedAt());
		}

	}

	/**
	 * A role's security-relevant state.
	 *
	 * @param id the role ID
	 * @param name the role name
	 * @param permissions the permissions the role grants, as {@code domain:action}
	 */
	public record RoleState(UUID id, String name, SortedSet<String> permissions) {

		public static RoleState of(AppRole role) {
			return new RoleState(role.getPublicId(), role.getName(),
					names(role.getPermissions().stream().map(AppPermission::getName).toList()));
		}

	}

	/**
	 * The details of {@code create_user}.
	 */
	record UserCreated(SortedSet<String> roles, SortedSet<String> permissions, boolean privileged) {
	}

	/**
	 * The details of {@code update_user}.
	 */
	record UserUpdated(Before before, List<String> fields, List<String> rolesAdded, List<String> rolesRemoved,
			List<String> permissionsAdded, List<String> permissionsRemoved, boolean privileged) {

		record Before(String status, SortedSet<String> roles, SortedSet<String> permissions, boolean privileged) {
		}

	}

	/**
	 * The details of {@code suspend_user} and {@code unsuspend_user}.
	 */
	record StatusChanged(String before) {
	}

	/**
	 * The details of {@code delete_user}, which the account review reads back as a
	 * {@link Removal}.
	 */
	record UserRemoved(String status, SortedSet<String> roles, SortedSet<String> permissions, boolean privileged,
			String department, Instant lastLoginAt, Instant lastActivityAt, Instant createdAt) {
	}

	/**
	 * The details of {@code revoke_sessions}.
	 */
	record SessionsRevoked(int revokedCount) {
	}

	/**
	 * The details of {@code create_role}, {@code update_role} and {@code delete_role}.
	 */
	record RoleChanged(String beforeName, List<String> permissionsAdded, List<String> permissionsRemoved,
			Long affectedUserCount) {
	}

	/**
	 * A removed account, as its removal recorded it.
	 *
	 * @param eventId the removal's event ID
	 * @param userId the account's public ID
	 * @param username the username
	 * @param name the account's name
	 * @param privileged whether the account was privileged
	 * @param department the department
	 * @param createdAt when the account was created, or null for a removal recorded
	 * before it was kept
	 * @param lastLoginAt the last sign-in time
	 * @param lastActivityAt when the account was last in use
	 * @param occurredAt when it was removed
	 * @param actor who removed it
	 * @param reasonCode the reason code
	 * @param reasonNote the note
	 */
	public record Removal(UUID eventId, UUID userId, String username, String name, boolean privileged,
			String department, Instant createdAt, Instant lastLoginAt, Instant lastActivityAt, Instant occurredAt,
			String actor, String reasonCode, String reasonNote) {
	}

	public void userCreated(UserState user) {
		this.trail.record(
				user(CREATE_USER, user).details(new UserCreated(user.roles(), user.permissions(), user.privileged()))
					.log("roles.added", List.copyOf(user.roles()))
					.log("permissions.added", List.copyOf(user.permissions()))
					.build());
	}

	public void userUpdated(UserState before, UserState after) {
		userUpdated(before, after, null);
	}

	/**
	 * Records a user change, with the reason the application made it, if it did.
	 * @param before the user's state before the change
	 * @param after the user's state after the change
	 * @param reason the controlled reason, such as {@code dormant_account}, or null
	 */
	public void userUpdated(UserState before, UserState after, String reason) {
		List<String> rolesAdded = added(before.roles(), after.roles());
		List<String> rolesRemoved = added(after.roles(), before.roles());
		List<String> permissionsAdded = added(before.permissions(), after.permissions());
		List<String> permissionsRemoved = added(after.permissions(), before.permissions());
		List<String> fields = new ArrayList<>();
		List<String> loggedFields = new ArrayList<>();
		if (!Objects.equals(before.email(), after.email())) {
			fields.add("email");
			loggedFields.add("email");
		}
		if (!Objects.equals(before.name(), after.name())) {
			fields.add("name");
			loggedFields.add("full_name");
		}
		AuditEvent.Builder event = AuditEvent.of(UPDATE_USER, target(after))
			.reason(reason)
			.details(new UserUpdated(
					new UserUpdated.Before(before.status(), before.roles(), before.permissions(), before.privileged()),
					fields, rolesAdded, rolesRemoved, permissionsAdded, permissionsRemoved, after.privileged()));
		state(event, before);
		if (!before.status().equals(after.status())) {
			event.log("user.changes.status", after.status());
		}
		if (!before.roles().equals(after.roles())) {
			event.log("user.changes.role.name", List.copyOf(after.roles()));
		}
		if (!before.permissions().equals(after.permissions())) {
			event.log("user.changes.permissions", List.copyOf(after.permissions()));
		}
		if (before.privileged() != after.privileged()) {
			event.log("user.changes.privileged", after.privileged());
		}
		if (!loggedFields.isEmpty()) {
			event.log("user.changes.fields", loggedFields);
		}
		this.trail.record(event.log("roles.added", rolesAdded)
			.log("roles.removed", rolesRemoved)
			.log("permissions.added", permissionsAdded)
			.log("permissions.removed", permissionsRemoved)
			.build());
	}

	/**
	 * Records an account being suspended.
	 * @param before the account's state before the change
	 * @param after the account's state after the change
	 * @param reason the reason code
	 * @param note the optional note, or null
	 */
	public void userSuspended(UserState before, UserState after, ReasonCode reason, String note) {
		this.trail.record(user(SUSPEND_USER, before).reason(reason.value(), note)
			.details(new StatusChanged(before.status()))
			.log("user.changes.status", after.status())
			.build());
	}

	public void userUnsuspended(UserState before, UserState after) {
		this.trail.record(user(UNSUSPEND_USER, before).details(new StatusChanged(before.status()))
			.log("user.changes.status", after.status())
			.build());
	}

	/**
	 * Records an account being removed, with the reason it was removed.
	 * @param user the account's state before the removal
	 * @param reason the reason code
	 * @param note the optional note, or null
	 * @return the recorded removal
	 */
	public AuditRecord userDeleted(UserState user, ReasonCode reason, String note) {
		return this.trail.record(user(DELETE_USER, user).reason(reason.value(), note)
			.details(new UserRemoved(user.status(), user.roles(), user.permissions(), user.privileged(),
					user.department(), user.lastLoginAt(), user.lastActivityAt(), user.createdAt()))
			.log("roles.removed", List.copyOf(user.roles()))
			.log("permissions.removed", List.copyOf(user.permissions()))
			.build());
	}

	/**
	 * Records an administrator ending sessions: one user's, or, when {@code username} is
	 * null, every user's but their own. Each ended session is also logged as a
	 * {@code destroy_session} event with reason {@code administrative_revocation}.
	 * @param username the user whose sessions were ended, or null for every user
	 * @param revoked the number of sessions ended
	 */
	public void sessionsRevoked(String username, int revoked) {
		AuditEvent.Builder event = AuditEvent.of(REVOKE_SESSIONS, AuditTarget.user(USER, null, username, null))
			.details(new SessionsRevoked(revoked))
			.log("session.revoked_count", revoked);
		if (username != null) {
			event.log("user.target.name", username);
		}
		this.trail.record(event.build());
	}

	public void roleCreated(RoleState role) {
		this.trail
			.record(role(CREATE_ROLE, role).details(new RoleChanged(null, List.copyOf(role.permissions()), null, null))
				.log("permissions.added", List.copyOf(role.permissions()))
				.build());
	}

	public void roleUpdated(RoleState before, RoleState after, long affectedUserCount) {
		List<String> added = added(before.permissions(), after.permissions());
		List<String> removed = added(after.permissions(), before.permissions());
		AuditEvent.Builder event = AuditEvent.of(UPDATE_ROLE, AuditTarget.of(ROLE, after.id(), after.name()))
			.details(new RoleChanged(before.name(), added, removed, affectedUserCount));
		role(event, before);
		if (!before.name().equals(after.name())) {
			event.log("role.changes.name", after.name());
		}
		if (!before.permissions().equals(after.permissions())) {
			event.log("role.changes.permissions", List.copyOf(after.permissions()));
		}
		this.trail.record(event.log("role.affected_user_count", affectedUserCount)
			.log("permissions.added", added)
			.log("permissions.removed", removed)
			.build());
	}

	public void roleDeleted(RoleState role) {
		this.trail
			.record(role(DELETE_ROLE, role).details(new RoleChanged(null, null, List.copyOf(role.permissions()), null))
				.log("permissions.removed", List.copyOf(role.permissions()))
				.build());
	}

	public <X extends RuntimeException> X userCreationRejected(String username, String reason, Supplier<X> exception) {
		return this.trail.reject(AuditEvent.of(CREATE_USER, AuditTarget.user(USER, null, username, null))
			.reason(reason)
			.log("user.target.name", username)
			.build(), exception);
	}

	public <X extends RuntimeException> X userUpdateRejected(UserState user, String reason, Supplier<X> exception) {
		return this.trail.reject(user(UPDATE_USER, user).reason(reason).build(), exception);
	}

	public <X extends RuntimeException> X userDeletionRejected(UserState user, String reason, Supplier<X> exception) {
		return this.trail.reject(user(DELETE_USER, user).reason(reason).build(), exception);
	}

	public <X extends RuntimeException> X userSuspensionRejected(UserState user, String reason, Supplier<X> exception) {
		return this.trail.reject(user(SUSPEND_USER, user).reason(reason).build(), exception);
	}

	public <X extends RuntimeException> X userUnsuspensionRejected(UserState user, String reason,
			Supplier<X> exception) {
		return this.trail.reject(user(UNSUSPEND_USER, user).reason(reason).build(), exception);
	}

	public <X extends RuntimeException> X roleCreationRejected(String name, String reason, Supplier<X> exception) {
		return this.trail.reject(AuditEvent.of(CREATE_ROLE, AuditTarget.of(ROLE, null, name))
			.reason(reason)
			.log("role.name", name)
			.build(), exception);
	}

	public <X extends RuntimeException> X roleUpdateRejected(RoleState role, String requestedName, String reason,
			Supplier<X> exception) {
		AuditEvent.Builder event = role(UPDATE_ROLE, role).reason(reason);
		if (!role.name().equals(requestedName)) {
			event.log("role.changes.name", requestedName);
		}
		return this.trail.reject(event.build(), exception);
	}

	public <X extends RuntimeException> X roleDeletionRejected(RoleState role, String reason, Supplier<X> exception) {
		return this.trail.reject(role(DELETE_ROLE, role).reason(reason).build(), exception);
	}

	/**
	 * Returns the removals of privileged or of non-privileged accounts since an instant,
	 * oldest first. Whether the account was privileged is recorded with its removal, and
	 * a removal that records nothing counts as not privileged.
	 * @param since the earliest removal time, or null for every recorded removal
	 * @param privileged whether to return the removals of privileged accounts
	 * @return the removals
	 */
	public List<Removal> removalsSince(Instant since, boolean privileged) {
		return this.trail.find(AuditQuery.where().action(DELETE_USER.name()).targetType(USER).since(since))
			.stream()
			.map(AccountAudit::removal)
			.filter(removal -> removal.privileged() == privileged)
			.toList();
	}

	/**
	 * Returns who last suspended each of the accounts that has a suspension in the audit
	 * trail.
	 * @param userIds the accounts' public IDs
	 * @return the actor by account ID
	 */
	public Map<UUID, String> lastSuspenders(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> suspenders = new HashMap<>();
		for (AuditRecord record : this.trail.find(AuditQuery.where()
			.action(SUSPEND_USER.name())
			.targetIds(userIds.stream().map(UUID::toString).toList()))) {
			suspenders.put(UUID.fromString(record.target().id()), record.actor());
		}
		return suspenders;
	}

	/**
	 * Returns the reason code of each of the removals.
	 * @param eventIds the removals' event IDs
	 * @return the reason code by event ID
	 */
	public Map<UUID, String> removalReasons(Collection<UUID> eventIds) {
		if (eventIds.isEmpty()) {
			return Map.of();
		}
		return this.trail.find(AuditQuery.where().ids(eventIds))
			.stream()
			.filter(record -> record.reasonCode() != null)
			.collect(Collectors.toMap(AuditRecord::id, AuditRecord::reasonCode));
	}

	private static Removal removal(AuditRecord record) {
		UserRemoved details = record.details(UserRemoved.class);
		AuditTarget target = record.target();
		return new Removal(record.id(), UUID.fromString(target.id()), target.name(), target.fullName(),
				details != null && details.privileged(), details == null ? null : details.department(),
				details == null ? null : details.createdAt(), details == null ? null : details.lastLoginAt(),
				details == null ? null : details.lastActivityAt(), record.occurredAt(), record.actor(),
				record.reasonCode(), record.reasonNote());
	}

	private static AuditTarget target(UserState user) {
		return AuditTarget.user(USER, user.id(), user.username(), user.name());
	}

	/**
	 * Starts a user event with the user's state before the change as
	 * {@code user.target.*}.
	 */
	private static AuditEvent.Builder user(AuditAction action, UserState user) {
		return state(AuditEvent.of(action, target(user)), user);
	}

	private static AuditEvent.Builder state(AuditEvent.Builder event, UserState user) {
		return event.log("user.target.id", user.id())
			.log("user.target.name", user.username())
			.log("user.target.status", user.status())
			.log("user.target.role.name", List.copyOf(user.roles()))
			.log("user.target.permissions", List.copyOf(user.permissions()))
			.log("user.target.privileged", user.privileged());
	}

	/**
	 * Starts a role event with the role's state before the change as {@code role.*}.
	 */
	private static AuditEvent.Builder role(AuditAction action, RoleState role) {
		return role(AuditEvent.of(action, AuditTarget.of(ROLE, role.id(), role.name())), role);
	}

	private static AuditEvent.Builder role(AuditEvent.Builder event, RoleState role) {
		return event.log("role.id", role.id())
			.log("role.name", role.name())
			.log("role.permissions", List.copyOf(role.permissions()));
	}

	/**
	 * Returns the sorted values in {@code after} that are not in {@code before}.
	 */
	private static List<String> added(Set<String> before, Set<String> after) {
		return after.stream().filter(value -> !before.contains(value)).sorted().toList();
	}

	private static SortedSet<String> names(Collection<String> names) {
		return new TreeSet<>(names);
	}

}
