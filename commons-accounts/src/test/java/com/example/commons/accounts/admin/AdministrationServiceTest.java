package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Tests {@link AdministrationService} against the real user, group, and role schema: it
 * revokes a user's active sessions exactly when their disabled status or group membership
 * changes, rejects changes that would break the model's integrity with a
 * {@link ConflictException}, and reports an unknown user, group, or role with a
 * {@link ResourceNotFoundException}.
 */
@AccountsJpaTest
@ExtendWith(OutputCaptureExtension.class)
class AdministrationServiceTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppGroupRepository groups;

	@Autowired
	private AppRoleRepository roles;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = new SessionLifecycleAuditLogger();

	private final SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();

	private final InMemorySessionRepository sessionRepository = new InMemorySessionRepository();

	private AdministrationService service;

	private AppRole userManage;

	private AppGroup managers;

	private AppGroup administrators;

	private AppUser testUser;

	@BeforeEach
	void setUp() {
		this.service = new AdministrationService(this.users, this.groups, this.roles, new SessionRevocationService(
				this.sessionRegistry, this.sessionRepository, this.sessionLifecycleAuditLogger));
		this.userManage = this.entityManager.persist(new AppRole("USER_MANAGE"));
		this.managers = this.entityManager.persist(new AppGroup("Managers"));
		this.managers.getRoles().add(this.userManage);
		this.administrators = this.entityManager.persist(new AppGroup("Administrators"));
		this.testUser = new AppUser("test-user", "Test User", "test@example.test", true);
		this.testUser.getGroups().add(this.managers);
		this.entityManager.persist(this.testUser);
		this.entityManager.flush();
		signIn("session-1", "test-user");
	}

	@Test
	void revokesSessionsWhenAUserIsDisabled(CapturedOutput output) {
		this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Test User", "test@example.test",
				false, Set.of(this.managers.getId())));

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(output).contains("destroy_session").contains("\"privilege_change\"");
	}

	@Test
	void revokesSessionsWhenGroupMembershipChanges() {
		this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Test User", "test@example.test",
				true, Set.of(this.administrators.getId())));

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(reload(this.testUser).getGroups()).extracting(AppGroup::getName).containsExactly("Administrators");
	}

	@Test
	void doesNotRevokeSessionsWhenNeitherEnabledStatusNorGroupsChange() {
		this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("New Display Name",
				"test@example.test", true, Set.of(this.managers.getId())));

		assertThat(sessionOf("test-user").isExpired()).isFalse();
		assertThat(reload(this.testUser).getDisplayName()).isEqualTo("New Display Name");
	}

	@Test
	void revokesSessionsWhenAUserIsDeleted(CapturedOutput output) {
		this.service.deleteUser(this.testUser.getId());
		this.entityManager.flush();

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(output).contains("destroy_session").contains("\"account_deleted\"");
		assertThat(this.users.existsByUsername("test-user")).isFalse();
	}

	@Test
	void rejectsADuplicateUsername() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("test-user", "Test User", null,
					true, Set.of(this.managers.getId()))))
			.withMessage("Username already exists.");
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void rejectsAUserInAGroupThatDoesNotExist() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null,
					true, Set.of(this.managers.getId(), "missing"))))
			.withMessage("Group was not found.");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
	}

	@Test
	void reportsAnUnknownUserGroupOrRole() {
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.user("missing"))
			.withMessage("User was not found.");
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.group("missing"))
			.withMessage("Group was not found.");
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.role("missing"))
			.withMessage("Role was not found.");
	}

	@Test
	void doesNotRevokeSessionsOrDeleteAnUnknownUser() {
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.deleteUser("missing"));

		assertThat(sessionOf("test-user").isExpired()).isFalse();
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void findsTheUsersOfAGroupOnceEach() {
		AppUser other = new AppUser("other-user", "Other User", null, true);
		other.getGroups().add(this.administrators);
		this.entityManager.persist(other);
		this.testUser.getGroups().add(this.administrators);
		this.entityManager.flush();

		assertThat(this.service.users(null, null, null, this.administrators.getId(), Pageable.unpaged()).getContent())
			.extracting(AppUser::getUsername)
			.containsExactlyInAnyOrder("test-user", "other-user");
		assertThat(this.service.users(null, null, null, this.managers.getId(), Pageable.unpaged()).getContent())
			.extracting(AppUser::getUsername)
			.containsExactly("test-user");
	}

	@Test
	void rejectsADuplicateGroupName() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createGroup(new AdminDtos.GroupRequest("Administrators", null)))
			.withMessage("Group name already exists.");
		assertThat(this.groups.count()).isEqualTo(2);
	}

	@Test
	void rejectsAGroupWithARoleThatDoesNotExist() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createGroup(new AdminDtos.GroupRequest("New Group", Set.of("missing"))))
			.withMessage("Role was not found.");
		assertThat(this.groups.existsByName("New Group")).isFalse();
	}

	@Test
	void rejectsRenamingAGroupToAnExistingName() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateGroup(this.managers.getId(),
					new AdminDtos.GroupRequest("Administrators", null)))
			.withMessage("Group name already exists.");
		assertThat(reload(this.managers).getName()).isEqualTo("Managers");
	}

	@Test
	void keepingAGroupsOwnNameIsNotAConflict() {
		AppGroup updated = this.service.updateGroup(this.managers.getId(),
				new AdminDtos.GroupRequest("Managers", Set.of(this.userManage.getId())));

		assertThat(updated.getName()).isEqualTo("Managers");
	}

	@Test
	void rejectsDeletingAGroupThatContainsUsers() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.deleteGroup(this.managers.getId()))
			.withMessage("Group contains users.");
		assertThat(this.groups.existsById(this.managers.getId())).isTrue();
	}

	@Test
	void deletesAGroupWithoutUsers() {
		this.service.deleteGroup(this.administrators.getId());
		this.entityManager.flush();

		assertThat(this.groups.existsById(this.administrators.getId())).isFalse();
	}

	@Test
	void rejectsADuplicateRoleName() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createRole(new AdminDtos.RoleRequest("USER_MANAGE")))
			.withMessage("Role name already exists.");
		assertThat(this.roles.count()).isEqualTo(1);
	}

	@Test
	void rejectsRenamingARoleToAnExistingName() {
		AppRole reportView = this.entityManager.persist(new AppRole("REPORT_VIEW"));

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateRole(reportView.getId(), new AdminDtos.RoleRequest("USER_MANAGE")))
			.withMessage("Role name already exists.");
		assertThat(reload(reportView).getName()).isEqualTo("REPORT_VIEW");
	}

	@Test
	void rejectsDeletingARoleThatIsAssignedToAGroup() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.deleteRole(this.userManage.getId()))
			.withMessage("Role is assigned to a group.");
		assertThat(this.roles.existsById(this.userManage.getId())).isTrue();
	}

	/**
	 * Registers an authenticated session for a user, with the audit identifier the login
	 * would have given it, so a revocation is both expired in the registry and logged.
	 */
	private void signIn(String sessionId, String username) {
		this.sessionRegistry.registerNewSession(sessionId, username);
		MockHttpSession httpSession = new MockHttpSession(null, sessionId);
		this.sessionLifecycleAuditLogger.logSessionCreatedIfNeeded(httpSession);
		MapSession session = new MapSession(sessionId);
		for (String name : Collections.list(httpSession.getAttributeNames())) {
			session.setAttribute(name, httpSession.getAttribute(name));
		}
		this.sessionRepository.save(session);
	}

	private SessionInformation sessionOf(String username) {
		return this.sessionRegistry.getAllSessions(username, true).get(0);
	}

	private <T> T reload(T entity) {
		this.entityManager.flush();
		this.entityManager.clear();
		Object id = this.entityManager.getId(entity);
		@SuppressWarnings("unchecked")
		T reloaded = (T) this.entityManager.find(entity.getClass(), id);
		return reloaded;
	}

	/**
	 * A {@link FindByIndexNameSessionRepository} held in memory, standing in for the JDBC
	 * one.
	 */
	static final class InMemorySessionRepository implements FindByIndexNameSessionRepository<MapSession> {

		private final Map<String, MapSession> sessions = new HashMap<>();

		@Override
		public MapSession createSession() {
			return new MapSession();
		}

		@Override
		public void save(MapSession session) {
			this.sessions.put(session.getId(), session);
		}

		@Override
		public MapSession findById(String id) {
			return this.sessions.get(id);
		}

		@Override
		public void deleteById(String id) {
			this.sessions.remove(id);
		}

		@Override
		public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
			return Map.of();
		}

	}

}
