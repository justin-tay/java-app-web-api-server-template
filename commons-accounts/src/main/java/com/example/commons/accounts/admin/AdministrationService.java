package com.example.commons.accounts.admin;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

@Transactional
public class AdministrationService {

	private final AppUserRepository users;

	private final AppGroupRepository groups;

	private final AppRoleRepository roles;

	private final SessionRevocationService sessionRevocationService;

	private final AdministrationAuditLogger auditLogger;

	public AdministrationService(AppUserRepository users, AppGroupRepository groups, AppRoleRepository roles,
			SessionRevocationService sessionRevocationService, AdministrationAuditLogger auditLogger) {
		this.users = users;
		this.groups = groups;
		this.roles = roles;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogger = auditLogger;
	}

	public AppUser createUser(AdminDtos.UserCreateRequest request) {
		if (this.users.existsByUsername(request.username())) {
			this.auditLogger.userCreationRejected(request.username(), "username_exists");
			throw new ConflictException("Username already exists.");
		}
		AppUser user = new AppUser(request.username(), request.displayName(), request.email(), request.enabled());
		user.getGroups().addAll(groups(request.groupIds()));
		AppUser saved = this.users.save(user);
		this.auditLogger.userCreated(UserState.of(saved));
		return saved;
	}

	public AppUser updateUser(String id, AdminDtos.UserUpdateRequest request) {
		AppUser user = user(id);
		UserState before = UserState.of(user);
		Set<String> previousGroupIds = user.getGroups().stream().map(AppGroup::getId).collect(Collectors.toSet());
		user.update(request.displayName(), request.email(), request.enabled());
		user.getGroups().clear();
		user.getGroups().addAll(groups(request.groupIds()));
		if ((before.enabled() && !user.isEnabled()) || !previousGroupIds.equals(request.groupIds())) {
			this.sessionRevocationService.revoke(user.getUsername(), "privilege_change");
		}
		this.auditLogger.userUpdated(before, UserState.of(user));
		return user;
	}

	public void deleteUser(String id) {
		AppUser user = user(id);
		UserState before = UserState.of(user);
		this.sessionRevocationService.revoke(user.getUsername(), "account_deleted");
		this.users.delete(user);
		this.auditLogger.userDeleted(before);
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
		AppGroup group = new AppGroup(request.name());
		group.getRoles().addAll(roles(request.roleIds()));
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
		group.setName(request.name());
		group.getRoles().clear();
		group.getRoles().addAll(roles(request.roleIds()));
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

	public AppRole updateRole(String id, AdminDtos.RoleRequest request) {
		AppRole role = role(id);
		RoleState before = RoleState.of(role);
		if (!role.getName().equals(request.name()) && this.roles.existsByName(request.name())) {
			this.auditLogger.roleUpdateRejected(before, request.name(), "name_exists");
			throw new ConflictException("Role name already exists.");
		}
		role.setName(request.name());
		this.auditLogger.roleUpdated(before, RoleState.of(role));
		return role;
	}

	public void deleteRole(String id) {
		AppRole role = role(id);
		RoleState before = RoleState.of(role);
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
