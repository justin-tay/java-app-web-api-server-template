package com.example.app.web.server.api.admin;

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

import com.example.app.web.server.domain.AppGroup;
import com.example.app.web.server.domain.AppGroupRepository;
import com.example.app.web.server.domain.AppRoleRepository;
import com.example.app.web.server.domain.AppUser;
import com.example.app.web.server.domain.AppUserRepository;
import com.example.app.web.server.security.session.SessionRevocationService;

/**
 * Tests that {@link AdministrationService} revokes a user's active sessions exactly when
 * their disabled status or group membership changes.
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
