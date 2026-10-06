package com.example.commons.accounts.admin;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.admin.AccountAuditLogger.RoleState;
import com.example.commons.accounts.admin.AccountAuditLogger.UserState;
import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppPermissionRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Changes the local user, role, and permission model, logging every change and rejection
 * through {@link AccountAuditLogger}.
 *
 * <p>
 * A change needs the permission for what it does: {@code user:add-role} to give a user a
 * role and {@code user:remove-role} to take one away, and {@code role:add-permission} and
 * {@code role:remove-permission} for a role's permissions. Granting is kept from going
 * beyond the grantor (see docs/adr/0038): an actor can grant only the privileged
 * permissions they hold themselves, and cannot change their own roles, or suspend,
 * unsuspend, or remove themselves (see {@link AccountLifecycleService}). No user may hold
 * two permissions the schema lists as conflicting, whether through one role or several.
 * These checks apply to an authenticated user; a change the application makes itself,
 * with no authenticated user, is trusted.
 */
@Transactional
public class AdministrationService {

	private final AppUserRepository users;

	private final AppRoleRepository roles;

	private final AppPermissionRepository permissions;

	private final SessionRevocationService sessionRevocationService;

	private final AccountAuditLogger auditLogger;

	public AdministrationService(AppUserRepository users, AppRoleRepository roles, AppPermissionRepository permissions,
			SessionRevocationService sessionRevocationService, AccountAuditLogger auditLogger) {
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogger = auditLogger;
	}

	public AppUser createUser(AdminDtos.UserCreateRequest request) {
		if (this.users.existsByUsername(request.username())) {
			this.auditLogger.userCreationRejected(request.username(), "username_exists");
			throw new ConflictException("Username already exists.");
		}
		Set<AppRole> requestedRoles = roles(request.roleIds());
		if (!Actor.currentHolds(Permissions.USER_ADD_ROLE)) {
			this.auditLogger.userCreationRejected(request.username(), "missing_permission");
			throw new AccessDeniedException("Giving a user a role needs " + Permissions.USER_ADD_ROLE + ".");
		}
		if (!mayGrantRoles(requestedRoles)) {
			this.auditLogger.userCreationRejected(request.username(), "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a privileged permission the actor does not hold.");
		}
		if (conflict(permissionsOf(requestedRoles)).isPresent()) {
			this.auditLogger.userCreationRejected(request.username(), "separation_of_duties");
			throw new ConflictException("The roles give the user permissions that must not be held together.");
		}
		AppUser user = new AppUser(request.username(), request.name(), request.email(),
				blankToNull(request.department()));
		user.getRoles().addAll(requestedRoles);
		AppUser saved = this.users.save(user);
		this.auditLogger.userCreated(UserState.of(saved));
		return saved;
	}

	public AppUser updateUser(UUID id, AdminDtos.UserUpdateRequest request) {
		AppUser user = user(id);
		UserState before = UserState.of(user);
		Set<UUID> previousRoleIds = user.getRoles().stream().map(AppRole::getPublicId).collect(Collectors.toSet());
		Set<AppRole> requestedRoles = roles(request.roleIds());
		boolean accessChanged = !previousRoleIds.equals(request.roleIds());
		if (accessChanged && isActor(user)) {
			this.auditLogger.userUpdateRejected(before, "self_modification");
			throw new AccessDeniedException("Users cannot change their own access.");
		}
		Set<AppRole> addedRoles = new HashSet<>(requestedRoles);
		addedRoles.removeAll(user.getRoles());
		Set<AppRole> removedRoles = new HashSet<>(user.getRoles());
		removedRoles.removeAll(requestedRoles);
		boolean detailsChanged = !Objects.equals(user.getName(), request.name())
				|| !Objects.equals(user.getEmail(), request.email())
				|| !Objects.equals(user.getDepartment(), blankToNull(request.department()));
		if ((!addedRoles.isEmpty() && !Actor.currentHolds(Permissions.USER_ADD_ROLE))
				|| (!removedRoles.isEmpty() && !Actor.currentHolds(Permissions.USER_REMOVE_ROLE))
				|| (detailsChanged && !Actor.currentHolds(Permissions.USER_UPDATE))) {
			this.auditLogger.userUpdateRejected(before, "missing_permission");
			throw new AccessDeniedException("The change needs permissions the actor does not hold.");
		}
		if (!mayGrantRoles(addedRoles)) {
			this.auditLogger.userUpdateRejected(before, "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a privileged permission the actor does not hold.");
		}
		if (!addedRoles.isEmpty() && conflict(permissionsOf(requestedRoles)).isPresent()) {
			this.auditLogger.userUpdateRejected(before, "separation_of_duties");
			throw new ConflictException("The roles give the user permissions that must not be held together.");
		}
		user.update(request.name(), request.email(), blankToNull(request.department()));
		user.getRoles().clear();
		user.getRoles().addAll(requestedRoles);
		if (accessChanged) {
			this.sessionRevocationService.revoke(user.getUsername(), "privilege_change");
		}
		this.auditLogger.userUpdated(before, UserState.of(user));
		return user;
	}

	/**
	 * Ends every session of a user without changing their account, such as when their
	 * session may have been taken over.
	 * @param id the user ID
	 */
	public void revokeSessions(UUID id) {
		AppUser user = user(id);
		int revoked = this.sessionRevocationService.revoke(user.getUsername(), "administrative_revocation");
		this.auditLogger.sessionsRevoked(user.getUsername(), revoked);
	}

	/**
	 * Ends the sessions of every local user except the one making the request, who stays
	 * signed in to respond to the incident that prompted it.
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

	/**
	 * Returns the distinct departments in use, for a filter control.
	 */
	@Transactional(readOnly = true)
	public List<String> departments() {
		return this.users.findDistinctDepartments();
	}

	public AppUser user(UUID id) {
		return this.users.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("User"));
	}

	/**
	 * Criteria for listing users. Every non-null value narrows the result, and
	 * {@code search} matches a username, name, or email containing it, or an exact ID.
	 */
	public record UserQuery(String search, String username, String name, String email, String department,
			AccountStatus status, Boolean neverSignedIn, UUID roleId, Boolean privileged, LocalDate createdFrom,
			LocalDate createdTo) {
	}

	public Page<AppUser> users(UserQuery query, Pageable pageable) {
		Specification<AppUser> search = isBlank(query.search()) ? null
				: Specification.anyOf(Stream.of(this.<AppUser>contains("username", query.search()),
						this.<AppUser>contains("name", query.search()), this.<AppUser>contains("email", query.search()),
						this.<AppUser>contains("department", query.search()),
						this.<AppUser>equals("publicId", asUuid(query.search())))
					.filter(Objects::nonNull)
					.toList());
		Specification<AppUser> specification = Specification.allOf(Stream
			.of(search, this.<AppUser>contains("username", query.username()),
					this.<AppUser>contains("name", query.name()), this.<AppUser>contains("email", query.email()),
					this.<AppUser>equals("department", blankToNull(query.department())),
					query.status() == null ? null : this.<AppUser>equals("status", query.status()),
					neverSignedIn(query.neverSignedIn()), privileged(query.privileged()),
					query.roleId() == null ? null
							: (Specification<AppUser>) (root, criteria, builder) -> builder
								.equal(root.join("roles").get("publicId"), query.roleId()),
					query.createdFrom() == null ? null
							: (Specification<AppUser>) (root, criteria, builder) -> builder.greaterThanOrEqualTo(
									root.get("createdAt"),
									query.createdFrom().atStartOfDay(ZoneOffset.UTC).toInstant()),
					query.createdTo() == null ? null
							: (Specification<AppUser>) (root, criteria, builder) -> builder.lessThan(
									root.get("createdAt"),
									query.createdTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
			.filter(value -> value != null)
			.toList());
		return this.users.findAll(distinct(specification), pageable);
	}

	public AppRole createRole(AdminDtos.RoleRequest request) {
		if (this.roles.existsByName(request.name())) {
			this.auditLogger.roleCreationRejected(request.name(), "name_exists");
			throw new ConflictException("Role name already exists.");
		}
		Set<AppPermission> requested = permissions(request.permissionIds());
		if (!requested.isEmpty() && !Actor.currentHolds(Permissions.ROLE_ADD_PERMISSION)) {
			this.auditLogger.roleCreationRejected(request.name(), "missing_permission");
			throw new AccessDeniedException(
					"Giving a role a permission needs " + Permissions.ROLE_ADD_PERMISSION + ".");
		}
		if (!mayGrant(requested)) {
			this.auditLogger.roleCreationRejected(request.name(), "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a privileged permission the actor does not hold.");
		}
		if (conflict(requested).isPresent()) {
			this.auditLogger.roleCreationRejected(request.name(), "separation_of_duties");
			throw new ConflictException("The permissions must not be held together.");
		}
		AppRole role = new AppRole(request.name());
		role.getPermissions().addAll(requested);
		AppRole saved = this.roles.save(role);
		this.auditLogger.roleCreated(RoleState.of(saved));
		return saved;
	}

	public AppRole updateRole(UUID id, AdminDtos.RoleRequest request) {
		AppRole role = role(id);
		RoleState before = RoleState.of(role);
		boolean renamed = !role.getName().equals(request.name());
		if (renamed && this.roles.existsByName(request.name())) {
			this.auditLogger.roleUpdateRejected(before, request.name(), "name_exists");
			throw new ConflictException("Role name already exists.");
		}
		Set<AppPermission> requested = permissions(request.permissionIds());
		Set<AppPermission> added = new HashSet<>(requested);
		added.removeAll(role.getPermissions());
		Set<AppPermission> removed = new HashSet<>(role.getPermissions());
		removed.removeAll(requested);
		if ((renamed && !Actor.currentHolds(Permissions.ROLE_UPDATE))
				|| (!added.isEmpty() && !Actor.currentHolds(Permissions.ROLE_ADD_PERMISSION))
				|| (!removed.isEmpty() && !Actor.currentHolds(Permissions.ROLE_REMOVE_PERMISSION))) {
			this.auditLogger.roleUpdateRejected(before, request.name(), "missing_permission");
			throw new AccessDeniedException("The change needs permissions the actor does not hold.");
		}
		if (!mayGrant(added)) {
			this.auditLogger.roleUpdateRejected(before, request.name(), "exceeds_actor_privileges");
			throw new AccessDeniedException("Cannot grant a privileged permission the actor does not hold.");
		}
		if (!added.isEmpty() && conflictsForHolders(role, requested)) {
			this.auditLogger.roleUpdateRejected(before, request.name(), "separation_of_duties");
			throw new ConflictException("The permissions must not be held together by a user of the role.");
		}
		role.setName(request.name());
		role.getPermissions().clear();
		role.getPermissions().addAll(requested);
		role.touch();
		this.auditLogger.roleUpdated(before, RoleState.of(role), this.users.countByRoles_PublicId(id));
		return role;
	}

	public void deleteRole(UUID id) {
		AppRole role = role(id);
		RoleState before = RoleState.of(role);
		if (this.users.existsByRoles_PublicId(id)) {
			this.auditLogger.roleDeletionRejected(before, "role_has_users");
			throw new ConflictException("Role has users.");
		}
		this.roles.delete(role);
		this.auditLogger.roleDeleted(before);
	}

	public AppRole role(UUID id) {
		return this.roles.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("Role"));
	}

	public Page<AppRole> roles(String search, String name, UUID permissionId, Pageable pageable) {
		Specification<AppRole> specification = Specification.allOf(Stream
			.of(this.<AppRole>contains("name", search), this.<AppRole>contains("name", name),
					permissionId == null ? null
							: (Specification<AppRole>) (root, query, builder) -> builder
								.equal(root.join("permissions").get("publicId"), permissionId))
			.filter(value -> value != null)
			.toList());
		return this.roles.findAll(distinct(specification), pageable);
	}

	public AppPermission permission(UUID id) {
		return this.permissions.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("Permission"));
	}

	@Transactional(readOnly = true)
	public Page<AppPermission> permissions(String search, String domain, Boolean privileged, Pageable pageable) {
		Specification<AppPermission> anyName = isBlank(search) ? null : Specification
			.anyOf(this.<AppPermission>contains("domain", search), this.<AppPermission>contains("action", search));
		Specification<AppPermission> specification = Specification.allOf(Stream
			.of(anyName, this.<AppPermission>contains("domain", domain),
					privileged == null ? null : this.<AppPermission>equals("privileged", privileged))
			.filter(value -> value != null)
			.toList());
		return this.permissions.findAll(specification, pageable);
	}

	/**
	 * Returns whether the user is the one making the change.
	 */
	private boolean isActor(AppUser user) {
		return Actor.current().map(actor -> actor.name().equals(user.getUsername())).orElse(false);
	}

	/**
	 * Returns whether the actor may grant every permission of the given roles, which is
	 * whether they hold each privileged one themselves.
	 */
	public static boolean mayGrantRoles(Collection<AppRole> roles) {
		return mayGrant(permissionsOf(roles));
	}

	/**
	 * Returns whether the actor may grant the given permissions: every privileged one
	 * must be one they hold. The others are reads, removals and review actions, which
	 * anyone with the permission to grant may. A change with no authenticated user is
	 * made by the application itself and is trusted.
	 */
	public static boolean mayGrant(Collection<AppPermission> permissions) {
		return Actor.current()
			.map(actor -> permissions.stream()
				.filter(AppPermission::isPrivileged)
				.map(AppPermission::getName)
				.allMatch(actor.permissions()::contains))
			.orElse(true);
	}

	/**
	 * Returns the permissions of all the given roles.
	 */
	public static Set<AppPermission> permissionsOf(Collection<AppRole> roles) {
		return roles.stream().flatMap(role -> role.getPermissions().stream()).collect(Collectors.toSet());
	}

	/**
	 * Returns a pair of the given permissions that no user may hold together, if there is
	 * one, such as reviewing accounts and any privileged permission (see docs/adr/0038).
	 * @param permissions the permissions one user would hold
	 * @return the first conflicting pair, named as {@code a and b}, or empty
	 */
	public static Optional<String> conflict(Collection<AppPermission> permissions) {
		List<AppPermission> list = new ArrayList<>(new LinkedHashSet<>(permissions));
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				if (list.get(i).conflictsWith(list.get(j))) {
					return Optional.of(list.get(i).getName() + " and " + list.get(j).getName());
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Returns whether giving the role the requested permissions would leave a user of the
	 * role with two that must not be held together, counting the user's other roles.
	 */
	private boolean conflictsForHolders(AppRole role, Set<AppPermission> requested) {
		if (conflict(requested).isPresent()) {
			return true;
		}
		for (AppUser holder : this.users.findByRoles_PublicId(role.getPublicId())) {
			Set<AppPermission> all = holder.getRoles()
				.stream()
				.filter(held -> !held.getPublicId().equals(role.getPublicId()))
				.flatMap(held -> held.getPermissions().stream())
				.collect(Collectors.toCollection(HashSet::new));
			all.addAll(requested);
			if (conflict(all).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private Set<AppRole> roles(Set<UUID> ids) {
		Set<AppRole> values = Set.copyOf(this.roles.findAllByPublicIdIn(ids));
		if (values.size() != ids.size())
			throw new ResourceNotFoundException("Role");
		return values;
	}

	private Set<AppPermission> permissions(Set<UUID> ids) {
		Set<AppPermission> values = Set.copyOf(this.permissions.findAllByPublicIdIn(ids));
		if (values.size() != ids.size())
			throw new ResourceNotFoundException("Permission");
		return values;
	}

	private <T> Specification<T> contains(String field, String value) {
		return isBlank(value) ? null : (root, query, builder) -> builder.like(builder.lower(root.get(field)),
				"%" + escapeLike(value.toLowerCase()) + "%", '\\');
	}

	private Specification<AppUser> neverSignedIn(Boolean neverSignedIn) {
		if (neverSignedIn == null) {
			return null;
		}
		return (root, query, builder) -> neverSignedIn ? builder.isNull(root.get("lastLoginAt"))
				: builder.isNotNull(root.get("lastLoginAt"));
	}

	/**
	 * Selects the privileged accounts, or the others: an account is privileged when one
	 * of its roles has a privileged permission.
	 */
	private Specification<AppUser> privileged(Boolean privileged) {
		if (privileged == null) {
			return null;
		}
		return (root, query, builder) -> {
			var subquery = query.subquery(Long.class);
			var inner = subquery.from(AppUser.class);
			var permission = inner.join("roles").join("permissions");
			subquery.select(inner.get("id"))
				.where(builder.equal(inner.get("id"), root.get("id")), builder.isTrue(permission.get("privileged")));
			return privileged ? builder.exists(subquery) : builder.not(builder.exists(subquery));
		};
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value.strip();
	}

	/**
	 * Escapes LIKE wildcards so user input matches literally.
	 */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * Returns the UUID the text spells, or null if it is not one, so a search term that
	 * is not an ID matches no ID.
	 */
	private static UUID asUuid(String text) {
		try {
			return text.length() == 36 ? UUID.fromString(text) : null;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
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
