package com.example.app.web.server.api.admin;

import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.app.web.server.domain.AppGroup;
import com.example.app.web.server.domain.AppGroupRepository;
import com.example.app.web.server.domain.AppRole;
import com.example.app.web.server.domain.AppRoleRepository;
import com.example.app.web.server.domain.AppUser;
import com.example.app.web.server.domain.AppUserRepository;

@Service
@Transactional
public class AdministrationService {

	private final AppUserRepository users;

	private final AppGroupRepository groups;

	private final AppRoleRepository roles;

	public AdministrationService(AppUserRepository users, AppGroupRepository groups, AppRoleRepository roles) {
		this.users = users;
		this.groups = groups;
		this.roles = roles;
	}

	public AppUser createUser(AdminDtos.UserCreateRequest request) {
		if (this.users.existsByUsername(request.username()))
			throw new ConflictException("Username already exists.");
		AppUser user = new AppUser(request.username(), request.displayName(), request.email(), request.enabled());
		user.getGroups().addAll(groups(request.groupIds()));
		return this.users.save(user);
	}

	public AppUser updateUser(String id, AdminDtos.UserUpdateRequest request) {
		AppUser user = user(id);
		user.update(request.displayName(), request.email(), request.enabled());
		user.getGroups().clear();
		user.getGroups().addAll(groups(request.groupIds()));
		return user;
	}

	public void deleteUser(String id) {
		this.users.delete(user(id));
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
		if (this.groups.existsByName(request.name()))
			throw new ConflictException("Group name already exists.");
		AppGroup group = new AppGroup(request.name());
		group.getRoles().addAll(roles(request.roleIds()));
		return this.groups.save(group);
	}

	public AppGroup updateGroup(String id, AdminDtos.GroupRequest request) {
		AppGroup group = group(id);
		if (!group.getName().equals(request.name()) && this.groups.existsByName(request.name()))
			throw new ConflictException("Group name already exists.");
		group.setName(request.name());
		group.getRoles().clear();
		group.getRoles().addAll(roles(request.roleIds()));
		group.touch();
		return group;
	}

	public void deleteGroup(String id) {
		if (this.users.existsByGroups_Id(id))
			throw new ConflictException("Group contains users.");
		this.groups.delete(group(id));
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
		if (this.roles.existsByName(request.name()))
			throw new ConflictException("Role name already exists.");
		return this.roles.save(new AppRole(request.name()));
	}

	public AppRole updateRole(String id, AdminDtos.RoleRequest request) {
		AppRole role = role(id);
		if (!role.getName().equals(request.name()) && this.roles.existsByName(request.name()))
			throw new ConflictException("Role name already exists.");
		role.setName(request.name());
		return role;
	}

	public void deleteRole(String id) {
		if (this.groups.existsByRoles_Id(id))
			throw new ConflictException("Role is assigned to a group.");
		this.roles.delete(role(id));
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
