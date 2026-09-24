package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Tests {@link AdministrationService}: it revokes a user's active sessions exactly when
 * their disabled status or group membership changes, rejects changes that would break the
 * user, group, and role model's integrity with a {@link ConflictException}, and reports
 * an unknown user, group, or role with a {@link ResourceNotFoundException}.
 */
class AdministrationServiceTest {

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final AppGroupRepository groups = mock(AppGroupRepository.class);

	private final AppRoleRepository roles = mock(AppRoleRepository.class);

	private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);

	private final AdministrationService service = new AdministrationService(this.users, this.groups, this.roles,
			this.sessionRevocationService);

	@Test
	void revokesSessionsWhenAUserIsDisabled() {
		AppGroup group = groupWithId("group-1");
		AppUser user = enabledUser("test-user", group);
		when(this.users.findById("user-1")).thenReturn(Optional.of(user));
		when(this.groups.findAllById(Set.of("group-1"))).thenReturn(List.of(group));

		this.service.updateUser("user-1",
				new AdminDtos.UserUpdateRequest("Display Name", "test@example.test", false, Set.of("group-1")));

		verify(this.sessionRevocationService).revoke("test-user", "privilege_change");
	}

	@Test
	void revokesSessionsWhenGroupMembershipChanges() {
		AppGroup originalGroup = groupWithId("group-1");
		AppGroup newGroup = groupWithId("group-2");
		AppUser user = enabledUser("test-user", originalGroup);
		when(this.users.findById("user-1")).thenReturn(Optional.of(user));
		when(this.groups.findAllById(Set.of("group-2"))).thenReturn(List.of(newGroup));

		this.service.updateUser("user-1",
				new AdminDtos.UserUpdateRequest("Display Name", "test@example.test", true, Set.of("group-2")));

		verify(this.sessionRevocationService).revoke("test-user", "privilege_change");
	}

	@Test
	void doesNotRevokeSessionsWhenNeitherEnabledStatusNorGroupsChange() {
		AppGroup group = groupWithId("group-1");
		AppUser user = enabledUser("test-user", group);
		when(this.users.findById("user-1")).thenReturn(Optional.of(user));
		when(this.groups.findAllById(Set.of("group-1"))).thenReturn(List.of(group));

		this.service.updateUser("user-1",
				new AdminDtos.UserUpdateRequest("New Display Name", "test@example.test", true, Set.of("group-1")));

		verify(this.sessionRevocationService, never()).revoke(any(), any());
	}

	@Test
	void revokesSessionsWhenAUserIsDeleted() {
		AppUser user = enabledUser("test-user", groupWithId("group-1"));
		when(this.users.findById("user-1")).thenReturn(Optional.of(user));

		this.service.deleteUser("user-1");

		verify(this.sessionRevocationService).revoke("test-user", "account_deleted");
	}

	@Test
	void rejectsADuplicateUsername() {
		when(this.users.existsByUsername("test-user")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service
				.createUser(new AdminDtos.UserCreateRequest("test-user", "Test User", null, true, Set.of("group-1"))))
			.withMessage("Username already exists.");
		verify(this.users, never()).save(any());
	}

	@Test
	void rejectsAUserInAGroupThatDoesNotExist() {
		when(this.groups.findAllById(Set.of("group-1", "missing"))).thenReturn(List.of(groupWithId("group-1")));

		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createUser(
					new AdminDtos.UserCreateRequest("new-user", "New User", null, true, Set.of("group-1", "missing"))))
			.withMessage("Group was not found.");
		verify(this.users, never()).save(any());
	}

	@Test
	void reportsAnUnknownUserGroupOrRole() {
		when(this.users.findById("missing")).thenReturn(Optional.empty());
		when(this.groups.findById("missing")).thenReturn(Optional.empty());
		when(this.roles.findById("missing")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.user("missing"))
			.withMessage("User was not found.");
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.group("missing"))
			.withMessage("Group was not found.");
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.role("missing"))
			.withMessage("Role was not found.");
	}

	@Test
	void doesNotRevokeSessionsOrDeleteAnUnknownUser() {
		when(this.users.findById("missing")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.deleteUser("missing"));
		verify(this.sessionRevocationService, never()).revoke(any(), any());
		verify(this.users, never()).delete(any(AppUser.class));
	}

	@Test
	void rejectsADuplicateGroupName() {
		when(this.groups.existsByName("Administrators")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createGroup(new AdminDtos.GroupRequest("Administrators", null)))
			.withMessage("Group name already exists.");
		verify(this.groups, never()).save(any());
	}

	@Test
	void rejectsAGroupWithARoleThatDoesNotExist() {
		when(this.roles.findAllById(Set.of("missing"))).thenReturn(List.of());

		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createGroup(new AdminDtos.GroupRequest("New Group", Set.of("missing"))))
			.withMessage("Role was not found.");
		verify(this.groups, never()).save(any());
	}

	@Test
	void rejectsRenamingAGroupToAnExistingName() {
		AppGroup group = groupWithId("group-1");
		when(this.groups.findById("group-1")).thenReturn(Optional.of(group));
		when(this.groups.existsByName("Administrators")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateGroup("group-1", new AdminDtos.GroupRequest("Administrators", null)))
			.withMessage("Group name already exists.");
		assertThat(group.getName()).isEqualTo("Group group-1");
	}

	@Test
	void keepingAGroupsOwnNameIsNotAConflict() {
		AppGroup group = groupWithId("group-1");
		when(this.groups.findById("group-1")).thenReturn(Optional.of(group));
		when(this.groups.existsByName("Group group-1")).thenReturn(true);

		assertThat(this.service.updateGroup("group-1", new AdminDtos.GroupRequest("Group group-1", null)).getName())
			.isEqualTo("Group group-1");
	}

	@Test
	void rejectsDeletingAGroupThatContainsUsers() {
		when(this.users.existsByGroups_Id("group-1")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.deleteGroup("group-1"))
			.withMessage("Group contains users.");
		verify(this.groups, never()).delete(any(AppGroup.class));
	}

	@Test
	void rejectsADuplicateRoleName() {
		when(this.roles.existsByName("USER_MANAGE")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createRole(new AdminDtos.RoleRequest("USER_MANAGE")))
			.withMessage("Role name already exists.");
		verify(this.roles, never()).save(any());
	}

	@Test
	void rejectsRenamingARoleToAnExistingName() {
		AppRole role = new AppRole("REPORT_VIEW");
		when(this.roles.findById("role-1")).thenReturn(Optional.of(role));
		when(this.roles.existsByName("USER_MANAGE")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateRole("role-1", new AdminDtos.RoleRequest("USER_MANAGE")))
			.withMessage("Role name already exists.");
		assertThat(role.getName()).isEqualTo("REPORT_VIEW");
	}

	@Test
	void rejectsDeletingARoleThatIsAssignedToAGroup() {
		when(this.groups.existsByRoles_Id("role-1")).thenReturn(true);

		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.deleteRole("role-1"))
			.withMessage("Role is assigned to a group.");
		verify(this.roles, never()).delete(any(AppRole.class));
	}

	private AppUser enabledUser(String username, AppGroup group) {
		AppUser user = new AppUser(username, "Display Name", "test@example.test", true);
		user.getGroups().add(group);
		return user;
	}

	private AppGroup groupWithId(String id) {
		AppGroup group = new AppGroup("Group " + id);
		ReflectionTestUtils.setField(group, "id", id);
		return group;
	}

}
