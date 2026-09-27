package com.example.commons.accounts.admin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.admin.AdministrationAuditLogger.GroupState;
import com.example.commons.accounts.admin.AdministrationAuditLogger.RoleState;
import com.example.commons.accounts.admin.AdministrationAuditLogger.UserState;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authentication.passkey.PasskeyManager;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Changes the local user, group, and role model, logging every change and rejection
 * through {@link AdministrationAuditLogger}.
 *
 * <p>
 * Each management role is kept from granting more than its holder has (see
 * docs/adr/0022): an administrator cannot grant a role they do not hold, whether by
 * giving a user a group or by giving a group a role; cannot change their own groups or
 * enabled status, or delete themselves; and cannot delete a {@link #RESERVED_ROLES
 * reserved role}. Role names cannot be changed at all, because a role's name is the
 * authority the application checks. These checks apply to an authenticated administrator;
 * a change the application makes itself, with no authenticated user, is trusted.
 */
@Transactional
public class AdministrationService {

	/**
	 * The roles the administration API itself requires. Deleting one would lock every
	 * administrator out of the part of the API it guards.
	 */
	public static final Set<String> RESERVED_ROLES = Set.of("USER_MANAGE", "GROUP_MANAGE", "ROLE_MANAGE");

	private final AppUserRepository users;

	private final AppGroupRepository groups;

	private final AppRoleRepository roles;

	private final SessionRevocationService sessionRevocationService;

	private final AdministrationAuditLogger auditLogger;

	private final PasskeyManager passkeyManager;

	public AdministrationService(AppUserRepository users, AppGroupRepository groups, AppRoleRepository roles,
			SessionRevocationService sessionRevocationService, AdministrationAuditLogger auditLogger) {
		this(users, groups, roles, sessionRevocationService, auditLogger, null);
	}

	/**
	 * Creates the service.
	 * @param passkeyManager the passkey manager, or null when passkeys are not enabled,
	 * used to delete a deleted user's passkeys
	 */
	public AdministrationService(AppUserRepository users, AppGroupRepository groups, AppRoleRepository roles,
			SessionRevocationService sessionRevocationService, AdministrationAuditLogger auditLogger,
			PasskeyManager passkeyManager) {
		this.users = users;
		this.groups = groups;
		this.roles = roles;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogger = auditLogger;
		this.passkeyManager = passkeyManager;
	}

	public AppUser createUser(AdminDtos.UserCreateRequest request) {
		if (this.users.existsByUsername(request.username())) {
			this.auditLogger.userCreationRejected(request.username(), "username_exists");
			throw new ConflictException("Username already exists.");
		}
		Set<AppGroup> requestedGroups = groups(request.groupIds());
		if (!holdsRolesOf(requestedGroups)) {
			this.auditLogger.userCreationRejected(request.username(), "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a role the administrator does not hold.");
		}
		AppUser user = new AppUser(request.username(), request.displayName(), request.email(), request.enabled());
		user.getGroups().addAll(requestedGroups);
		AppUser saved = this.users.save(user);
		this.auditLogger.userCreated(UserState.of(saved));
		return saved;
	}

	public AppUser updateUser(String id, AdminDtos.UserUpdateRequest request) {
		AppUser user = user(id);
		UserState before = UserState.of(user);
		Set<String> previousGroupIds = user.getGroups().stream().map(AppGroup::getId).collect(Collectors.toSet());
		Set<AppGroup> requestedGroups = groups(request.groupIds());
		boolean accessChanged = before.enabled() != request.enabled() || !previousGroupIds.equals(request.groupIds());
		if (accessChanged && isActor(user)) {
			this.auditLogger.userUpdateRejected(before, "self_modification");
			throw new AccessDeniedException("Administrators cannot change their own access.");
		}
		Set<AppGroup> addedGroups = new HashSet<>(requestedGroups);
		addedGroups.removeAll(user.getGroups());
		if (!holdsRolesOf(addedGroups)) {
			this.auditLogger.userUpdateRejected(before, "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a role the administrator does not hold.");
		}
		user.update(request.displayName(), request.email(), request.enabled());
		user.getGroups().clear();
		user.getGroups().addAll(requestedGroups);
		if ((before.enabled() && !user.isEnabled()) || !previousGroupIds.equals(request.groupIds())) {
			this.sessionRevocationService.revoke(user.getUsername(), "privilege_change");
		}
		this.auditLogger.userUpdated(before, UserState.of(user));
		return user;
	}

	public void deleteUser(String id) {
		AppUser user = user(id);
		UserState before = UserState.of(user);
		if (isActor(user)) {
			this.auditLogger.userDeletionRejected(before, "self_modification");
			throw new AccessDeniedException("Administrators cannot delete themselves.");
		}
		this.sessionRevocationService.revoke(user.getUsername(), "account_deleted");
		if (this.passkeyManager != null) {
			this.passkeyManager.removeAll(user.getId());
		}
		this.users.delete(user);
		this.auditLogger.userDeleted(before);
	}

	/**
	 * Ends every session of a user without changing their account, such as when their
	 * session may have been taken over.
	 * @param id the user ID
	 */
	public void revokeSessions(String id) {
		AppUser user = user(id);
		int revoked = this.sessionRevocationService.revoke(user.getUsername(), "administrative_revocation");
		this.auditLogger.sessionsRevoked(user.getUsername(), revoked);
	}

	/**
	 * Ends the sessions of every local user except the administrator making the request,
	 * who stays signed in to respond to the incident that prompted it.
	 */
	public void revokeAllSessions() {
		String actor = Actor.current().map(Actor::name).orElse(null);
		int revoked = 0;
		for (String username : this.users.findAllUsernames()) {
			if (!username.equals(actor)) {
				revoked += this.sessionRevocationService.revoke(username, "administrative_revocation");
			}
		}
		this.auditLogger.sessionsRevoked(null, revoked);
	}

	public AppUser user(String id) {
		return this.users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User"));
	}

	public Page<AppUser> users(String username, String displayName, Boolean enabled, String groupId,
			Pageable pageable) {
		if ((username == null || username.isBlank()) && (displayName == null || displayName.isBlank())
				&& enabled == null && groupId == null)
			return this.users.findAll(pageable);
		Specification<AppUser> specification = Specification.allOf(Stream
			.of(this.<AppUser>contains("username", username), this.<AppUser>contains("displayName", displayName),
					this.<AppUser>equals("enabled", enabled),
					groupId == null ? null
							: (root, query, builder) -> builder.equal(root.join("groups").get("id"), groupId))
			.filter(value -> value != null)
			.toList());
		return this.users.findAll(distinct(specification), pageable);
	}

	public AppGroup createGroup(AdminDtos.GroupRequest request) {
		if (this.groups.existsByName(request.name())) {
			this.auditLogger.groupCreationRejected(request.name(), "name_exists");
			throw new ConflictException("Group name already exists.");
		}
		Set<AppRole> requestedRoles = roles(request.roleIds());
		if (!holds(requestedRoles)) {
			this.auditLogger.groupCreationRejected(request.name(), "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a role the administrator does not hold.");
		}
		AppGroup group = new AppGroup(request.name());
		group.getRoles().addAll(requestedRoles);
		AppGroup saved = this.groups.save(group);
		this.auditLogger.groupCreated(GroupState.of(saved));
		return saved;
	}

	public AppGroup updateGroup(String id, AdminDtos.GroupRequest request) {
		AppGroup group = group(id);
		GroupState before = GroupState.of(group);
		if (!group.getName().equals(request.name()) && this.groups.existsByName(request.name())) {
			this.auditLogger.groupUpdateRejected(before, request.name(), "name_exists");
			throw new ConflictException("Group name already exists.");
		}
		Set<AppRole> requestedRoles = roles(request.roleIds());
		Set<AppRole> addedRoles = new HashSet<>(requestedRoles);
		addedRoles.removeAll(group.getRoles());
		if (!holds(addedRoles)) {
			this.auditLogger.groupUpdateRejected(before, request.name(), "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a role the administrator does not hold.");
		}
		group.setName(request.name());
		group.getRoles().clear();
		group.getRoles().addAll(requestedRoles);
		group.touch();
		this.auditLogger.groupUpdated(before, GroupState.of(group), this.users.countByGroups_Id(id));
		return group;
	}

	public void deleteGroup(String id) {
		AppGroup group = group(id);
		GroupState before = GroupState.of(group);
		if (this.users.existsByGroups_Id(id)) {
			this.auditLogger.groupDeletionRejected(before, "group_has_users");
			throw new ConflictException("Group contains users.");
		}
		this.groups.delete(group);
		this.auditLogger.groupDeleted(before);
	}

	public AppGroup group(String id) {
		return this.groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group"));
	}

	public Page<AppGroup> groups(String name, String roleId, Pageable pageable) {
		if ((name == null || name.isBlank()) && roleId == null)
			return this.groups.findAll(pageable);
		Specification<AppGroup> specification = Specification.allOf(Stream
			.of(this.<AppGroup>contains("name", name),
					roleId == null ? null
							: (root, query, builder) -> builder.equal(root.join("roles").get("id"), roleId))
			.filter(value -> value != null)
			.toList());
		return this.groups.findAll(distinct(specification), pageable);
	}

	public AppRole createRole(AdminDtos.RoleRequest request) {
		if (this.roles.existsByName(request.name())) {
			this.auditLogger.roleCreationRejected(request.name(), "name_exists");
			throw new ConflictException("Role name already exists.");
		}
		AppRole saved = this.roles.save(new AppRole(request.name()));
		this.auditLogger.roleCreated(RoleState.of(saved));
		return saved;
	}

	public void deleteRole(String id) {
		AppRole role = role(id);
		RoleState before = RoleState.of(role);
		if (RESERVED_ROLES.contains(role.getName())) {
			this.auditLogger.roleDeletionRejected(before, "reserved_role");
			throw new AccessDeniedException("Reserved roles cannot be deleted.");
		}
		if (this.groups.existsByRoles_Id(id)) {
			this.auditLogger.roleDeletionRejected(before, "role_in_use");
			throw new ConflictException("Role is assigned to a group.");
		}
		this.roles.delete(role);
		this.auditLogger.roleDeleted(before);
	}

	public AppRole role(String id) {
		return this.roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Role"));
	}

	public Page<AppRole> roles(String name, Pageable pageable) {
		if (name == null || name.isBlank())
			return this.roles.findAll(pageable);
		return this.roles.findAll(
				distinct(Specification
					.allOf(Stream.of(this.<AppRole>contains("name", name)).filter(value -> value != null).toList())),
				pageable);
	}

	/**
	 * Returns whether the user is the administrator making the change.
	 */
	private boolean isActor(AppUser user) {
		return Actor.current().map(actor -> actor.name().equals(user.getUsername())).orElse(false);
	}

	/**
	 * Returns whether the administrator holds every role the given groups grant.
	 */
	private boolean holdsRolesOf(Collection<AppGroup> groups) {
		return holds(groups.stream().flatMap(group -> group.getRoles().stream()).toList());
	}

	/**
	 * Returns whether the administrator holds every given role. A change with no
	 * authenticated administrator is made by the application itself and is trusted.
	 */
	private boolean holds(Collection<AppRole> roles) {
		return Actor.current()
			.map(actor -> roles.stream().map(AppRole::getName).allMatch(actor.roles()::contains))
			.orElse(true);
	}

	private Set<AppGroup> groups(Set<String> ids) {
		Set<AppGroup> values = Set.copyOf(this.groups.findAllById(ids));
		if (values.size() != ids.size())
			throw new ResourceNotFoundException("Group");
		return values;
	}

	private Set<AppRole> roles(Set<String> ids) {
		Set<AppRole> values = Set.copyOf(this.roles.findAllById(ids));
		if (values.size() != ids.size())
			throw new ResourceNotFoundException("Role");
		return values;
	}

	private <T> Specification<T> contains(String field, String value) {
		return value == null || value.isBlank() ? null : (root, query, builder) -> builder
			.like(builder.lower(root.get(field)), "%" + value.toLowerCase() + "%");
	}

	private <T> Specification<T> equals(String field, Object value) {
		return value == null ? null : (root, query, builder) -> builder.equal(root.get(field), value);
	}

	private <T> Specification<T> distinct(Specification<T> specification) {
		return (root, query, builder) -> {
			query.distinct(true);
			return specification.toPredicate(root, query, builder);
		};
	}

}
