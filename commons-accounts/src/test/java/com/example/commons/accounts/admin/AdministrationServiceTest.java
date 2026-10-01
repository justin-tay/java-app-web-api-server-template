package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.example.commons.accounts.domain.ReasonCode;
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

	private AccountLifecycleService lifecycle;

	private AppRole userManage;

	private AppGroup managers;

	private AppGroup administrators;

	private AppUser testUser;

	@BeforeEach
	void setUp() {
		SessionRevocationService revocation = new SessionRevocationService(this.sessionRegistry, this.sessionRepository,
				this.sessionLifecycleAuditLogger);
		AccountAuditLogger auditLogger = new AccountAuditLogger();
		this.service = new AdministrationService(this.users, this.groups, this.roles, revocation, auditLogger);
		this.lifecycle = new AccountLifecycleService(this.users, revocation, auditLogger, null, null,
				Clock.systemUTC());
		this.userManage = this.entityManager.persist(new AppRole("USER_MANAGE"));
		this.managers = this.entityManager.persist(new AppGroup("Managers"));
		this.managers.getRoles().add(this.userManage);
		this.administrators = this.entityManager.persist(new AppGroup("Administrators"));
		this.testUser = new AppUser("test-user", "Test User", "test@example.test");
		this.testUser.getGroups().add(this.managers);
		this.entityManager.persist(this.testUser);
		this.entityManager.flush();
		signIn("session-1", "test-user");
	}

	@Test
	void revokesSessionsAndRecordsWhyWhenAUserIsSuspended(CapturedOutput output) {
		this.lifecycle.suspend(this.testUser.getId(), ReasonCode.LEFT_ORGANISATION, "moved teams");

		AppUser suspended = reload(this.testUser);
		assertThat(suspended.isSuspended()).isTrue();
		assertThat(suspended.getSuspendedAt()).isNotNull();
		assertThat(suspended.getSuspensionReasonCode()).isEqualTo("left_organisation");
		assertThat(suspended.getSuspensionNote()).isEqualTo("moved teams");
		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(output).contains("destroy_session").contains("\"account_suspended\"");
	}

	@Test
	void unsuspendingRestartsTheInactivityClockWithoutTouchingTheLastLogin() {
		this.users.recordLogin("test-user", Instant.parse("2026-01-01T00:00:00Z"));
		this.entityManager.clear();
		this.lifecycle.suspend(this.testUser.getId(), ReasonCode.OTHER, null);

		this.lifecycle.unsuspend(this.testUser.getId());

		AppUser reactivated = reload(this.testUser);
		assertThat(reactivated.isSuspended()).isFalse();
		assertThat(reactivated.getSuspendedAt()).isNull();
		assertThat(reactivated.getSuspensionReasonCode()).isNull();
		assertThat(reactivated.getLastLoginAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
		assertThat(reactivated.getInactivityClockStartedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Test
	void rejectsSuspendingASuspendedUserAndUnsuspendingAnActiveOne() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.lifecycle.unsuspend(this.testUser.getId()))
			.withMessage("Account is not suspended.");
		this.lifecycle.suspend(this.testUser.getId(), ReasonCode.OTHER, null);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.lifecycle.suspend(this.testUser.getId(), ReasonCode.OTHER, null))
			.withMessage("Account is already suspended.");
	}

	@Test
	void revokesSessionsWhenGroupMembershipChanges() {
		this.service.updateUser(this.testUser.getId(),
				new AdminDtos.UserUpdateRequest("Test User", "test@example.test", Set.of(this.administrators.getId())));

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(reload(this.testUser).getGroups()).extracting(AppGroup::getName).containsExactly("Administrators");
	}

	@Test
	void doesNotRevokeSessionsWhenGroupsDoNotChange() {
		this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("New Display Name",
				"test@example.test", Set.of(this.managers.getId())));

		assertThat(sessionOf("test-user").isExpired()).isFalse();
		assertThat(reload(this.testUser).getName()).isEqualTo("New Display Name");
	}

	@Test
	void revokesSessionsWhenAUserIsRemoved(CapturedOutput output) {
		this.lifecycle.remove(this.testUser.getId(), ReasonCode.LEFT_ORGANISATION, null);
		this.entityManager.flush();

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(output).contains("destroy_session").contains("\"account_deleted\"");
		assertThat(this.users.existsByUsername("test-user")).isFalse();
	}

	@Test
	void rejectsADuplicateUsername() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createUser(
					new AdminDtos.UserCreateRequest("test-user", "Test User", null, Set.of(this.managers.getId()))))
			.withMessage("Username already exists.");
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void rejectsAUserInAGroupThatDoesNotExist() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null,
					Set.of(this.managers.getId(), "missing"))))
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
	void doesNotRevokeSessionsOrRemoveAnUnknownUser() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.lifecycle.remove("missing", ReasonCode.OTHER, null));

		assertThat(sessionOf("test-user").isExpired()).isFalse();
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void findsTheUsersOfAGroupOnceEach() {
		AppUser other = new AppUser("other-user", "Other User", null);
		other.getGroups().add(this.administrators);
		this.entityManager.persist(other);
		this.testUser.getGroups().add(this.administrators);
		this.entityManager.flush();

		assertThat(this.service
			.users(new AdministrationService.UserQuery(null, null, null, null, null, this.administrators.getId(), null,
					null), Pageable.unpaged())
			.getContent()).extracting(AppUser::getUsername).containsExactlyInAnyOrder("test-user", "other-user");
		assertThat(this.service
			.users(new AdministrationService.UserQuery(null, null, null, null, null, this.managers.getId(), null, null),
					Pageable.unpaged())
			.getContent()).extracting(AppUser::getUsername).containsExactly("test-user");
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
	void rejectsDeletingARoleThatIsAssignedToAGroup() {
		AppRole reportView = this.entityManager.persist(new AppRole("REPORT_VIEW"));
		this.administrators.getRoles().add(reportView);

		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.deleteRole(reportView.getId()))
			.withMessage("Role is assigned to a group.");
		assertThat(this.roles.existsById(reportView.getId())).isTrue();
	}

	@Test
	void anAdministratorCannotGiveAUserAGroupGrantingARoleTheyDoNotHold(CapturedOutput output) {
		AppRole groupManage = this.entityManager.persist(new AppRole("GROUP_MANAGE"));
		this.administrators.getRoles().add(groupManage);
		authenticate("admin", "USER_MANAGE");

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service.createUser(
				new AdminDtos.UserCreateRequest("new-user", "New User", null, Set.of(this.administrators.getId()))));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", Set.of(this.managers.getId(), this.administrators.getId()))));

		assertThat(output).contains("\"exceeds_actor_privileges\"");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
		assertThat(reload(this.testUser).getGroups()).extracting(AppGroup::getName).containsExactly("Managers");
	}

	@Test
	void anAdministratorCanMaintainAccountReviewersWithoutHoldingTheRole() {
		AppRole reviewer = this.entityManager.persist(new AppRole("ACCOUNT_REVIEWER"));
		AppGroup reviewers = this.entityManager.persist(new AppGroup("Account Reviewers"));
		reviewers.getRoles().add(reviewer);
		authenticate("admin", "USER_MANAGE", "GROUP_MANAGE");

		AppUser user = this.service.createUser(
				new AdminDtos.UserCreateRequest("new-reviewer", "New Reviewer", null, Set.of(reviewers.getId())));
		this.service.createGroup(new AdminDtos.GroupRequest("More Reviewers", Set.of(reviewer.getId())));

		assertThat(user.getGroups()).extracting(AppGroup::getName).containsExactly("Account Reviewers");
		assertThat(this.groups.existsByName("More Reviewers")).isTrue();
	}

	@Test
	void anAdministratorCannotMakeThemselvesAnAccountReviewer() {
		AppRole reviewer = this.entityManager.persist(new AppRole("ACCOUNT_REVIEWER"));
		AppGroup reviewers = this.entityManager.persist(new AppGroup("Account Reviewers"));
		reviewers.getRoles().add(reviewer);
		authenticate("test-user", "USER_MANAGE");

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", Set.of(this.managers.getId(), reviewers.getId()))));
	}

	@Test
	void anAdministratorCanGiveAGroupGrantingOnlyRolesTheyHold() {
		authenticate("admin", "USER_MANAGE");

		AppUser user = this.service
			.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null, Set.of(this.managers.getId())));

		assertThat(user.getGroups()).extracting(AppGroup::getName).containsExactly("Managers");
	}

	@Test
	void anAdministratorCannotChangeTheirOwnAccessOrSuspendOrRemoveThemselves(CapturedOutput output) {
		authenticate("test-user", "USER_MANAGE");

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.lifecycle.suspend(this.testUser.getId(), ReasonCode.OTHER, null));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", Set.of(this.administrators.getId()))));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.lifecycle.remove(this.testUser.getId(), ReasonCode.OTHER, null));

		assertThat(output).contains("\"self_modification\"");
		assertThat(sessionOf("test-user").isExpired()).isFalse();
	}

	@Test
	void anAdministratorCanChangeTheirOwnNameAndEmail() {
		authenticate("test-user", "USER_MANAGE");

		this.service.updateUser(this.testUser.getId(),
				new AdminDtos.UserUpdateRequest("Renamed User", "renamed@example.test", Set.of(this.managers.getId())));

		assertThat(reload(this.testUser).getName()).isEqualTo("Renamed User");
	}

	@Test
	void anAdministratorCannotGiveAGroupARoleTheyDoNotHold(CapturedOutput output) {
		AppRole reportView = this.entityManager.persist(new AppRole("REPORT_VIEW"));
		authenticate("admin", "GROUP_MANAGE");

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.createGroup(new AdminDtos.GroupRequest("Viewers", Set.of(reportView.getId()))));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.updateGroup(this.administrators.getId(),
					new AdminDtos.GroupRequest("Administrators", Set.of(reportView.getId()))));

		assertThat(output).contains("\"exceeds_actor_privileges\"");
		assertThat(reload(this.administrators).getRoles()).isEmpty();
	}

	@Test
	void anAdministratorCanKeepARoleTheyDoNotHoldOnAGroupTheyChange() {
		authenticate("admin", "GROUP_MANAGE");

		AppGroup group = this.service.updateGroup(this.managers.getId(),
				new AdminDtos.GroupRequest("Team Managers", Set.of(this.userManage.getId())));

		assertThat(group.getName()).isEqualTo("Team Managers");
		assertThat(group.getRoles()).containsExactly(this.userManage);
	}

	@Test
	void aReservedRoleCannotBeDeleted(CapturedOutput output) {
		AppRole roleManage = this.entityManager.persist(new AppRole("ROLE_MANAGE"));
		authenticate("admin", "ROLE_MANAGE");

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.deleteRole(roleManage.getId()));

		assertThat(output).contains("\"reserved_role\"");
		assertThat(this.roles.existsById(roleManage.getId())).isTrue();
	}

	@Test
	void revokesOneUsersSessionsWithoutChangingTheirAccount(CapturedOutput output) {
		this.service.revokeSessions(this.testUser.getId());

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(reload(this.testUser).isSuspended()).isFalse();
		assertThat(output).contains("\"administrative_revocation\"").contains("revoke_sessions");
	}

	@Test
	void revokesEveryUsersSessionsExceptTheCallers() {
		AppUser admin = new AppUser("admin", "Administrator", null);
		admin.getGroups().add(this.managers);
		this.entityManager.persist(admin);
		signIn("session-2", "admin");
		authenticate("admin", "USER_MANAGE");

		this.service.revokeAllSessions();

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(sessionOf("admin").isExpired()).isFalse();
	}

	private void authenticate(String username, String... roles) {
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(username, null,
					Arrays.stream(roles).map(role -> "ROLE_" + role).toArray(String[]::new)));
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
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
