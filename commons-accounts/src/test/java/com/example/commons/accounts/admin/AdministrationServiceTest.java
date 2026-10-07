package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

import com.example.commons.accounts.audit.AccountAudit;
import com.example.commons.audit.AuditQuery;
import com.example.commons.audit.AuditTrail;
import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppPermissionRepository;
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
 * Tests {@link AdministrationService} against the real user, role, and permission schema:
 * it revokes a user's active sessions exactly when their disabled status or role
 * membership changes, rejects changes that would break the model's integrity with a
 * {@link ConflictException}, keeps an actor from granting more than they hold, keeps
 * conflicting permissions apart, and reports an unknown user, role, or permission with a
 * {@link ResourceNotFoundException} (see docs/adr/0038).
 */
@AccountsJpaTest
@ExtendWith(OutputCaptureExtension.class)
class AdministrationServiceTest {

	private static final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-00000000dead");

	@Autowired
	private AuditTrail auditTrail;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppRoleRepository roles;

	@Autowired
	private AppPermissionRepository permissions;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = new SessionLifecycleAuditLogger();

	private final SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();

	private final InMemorySessionRepository sessionRepository = new InMemorySessionRepository();

	private AdministrationService service;

	private AccountLifecycleService lifecycle;

	private AppRole managers;

	private AppRole administrators;

	private AppRole reviewers;

	private AppUser testUser;

	@BeforeEach
	void setUp() {
		SessionRevocationService revocation = new SessionRevocationService(this.sessionRegistry, this.sessionRepository,
				this.sessionLifecycleAuditLogger);
		AccountAudit audit = new AccountAudit(this.auditTrail);
		this.service = new AdministrationService(this.users, this.roles, this.permissions, revocation, audit);
		this.lifecycle = new AccountLifecycleService(this.users, revocation, audit, null, null, Clock.systemUTC());
		this.managers = this.entityManager.persist(new AppRole("Managers"));
		this.managers.getPermissions().add(permission(Permissions.USER_READ));
		this.administrators = this.entityManager.persist(new AppRole("Administrators"));
		this.administrators.getPermissions().add(permission(Permissions.USER_CREATE));
		this.reviewers = this.entityManager.persist(new AppRole("Reviewers"));
		this.reviewers.getPermissions().add(permission(Permissions.REVIEW_DECIDE));
		this.testUser = new AppUser("test-user", "Test User", "test@example.test");
		this.testUser.getRoles().add(this.managers);
		this.entityManager.persist(this.testUser);
		this.entityManager.flush();
		signIn("session-1", "test-user");
	}

	@Test
	void revokesSessionsAndRecordsWhyWhenAUserIsSuspended(CapturedOutput output) {
		this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.LEFT_ORGANISATION, "moved teams");

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
		this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.OTHER, null);

		this.lifecycle.unsuspend(this.testUser.getPublicId());

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
			.isThrownBy(() -> this.lifecycle.unsuspend(this.testUser.getPublicId()))
			.withMessage("Account is not suspended.");
		this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.OTHER, null);

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.OTHER, null))
			.withMessage("Account is already suspended.");
	}

	@Test
	void revokesSessionsWhenRoleMembershipChanges() {
		this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
				"test@example.test", null, Set.of(this.administrators.getPublicId())));

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(reload(this.testUser).getRoles()).extracting(AppRole::getName).containsExactly("Administrators");
	}

	@Test
	void doesNotRevokeSessionsWhenRolesDoNotChange() {
		this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("New Display Name",
				"test@example.test", null, Set.of(this.managers.getPublicId())));

		assertThat(sessionOf("test-user").isExpired()).isFalse();
		assertThat(reload(this.testUser).getName()).isEqualTo("New Display Name");
	}

	@Test
	void revokesSessionsWhenAUserIsRemoved(CapturedOutput output) {
		this.lifecycle.remove(this.testUser.getPublicId(), ReasonCode.LEFT_ORGANISATION, null);
		this.entityManager.flush();

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(output).contains("destroy_session").contains("\"account_deleted\"");
		assertThat(this.users.existsByUsername("test-user")).isFalse();
	}

	@Test
	void rejectsADuplicateUsername() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("test-user", "Test User", null,
					null, Set.of(this.managers.getPublicId()))))
			.withMessage("Username already exists.");
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void rejectsAUserInARoleThatDoesNotExist() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null,
					null, Set.of(this.managers.getPublicId(), MISSING))))
			.withMessage("Role was not found.");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
	}

	@Test
	void reportsAnUnknownUserRoleOrPermission() {
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.user(MISSING))
			.withMessage("User was not found.");
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.role(MISSING))
			.withMessage("Role was not found.");
		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> this.service.permission(MISSING))
			.withMessage("Permission was not found.");
	}

	@Test
	void doesNotRevokeSessionsOrRemoveAnUnknownUser() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.lifecycle.remove(MISSING, ReasonCode.OTHER, null));

		assertThat(sessionOf("test-user").isExpired()).isFalse();
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void findsTheUsersOfARoleOnceEach() {
		AppUser other = new AppUser("other-user", "Other User", null);
		other.getRoles().add(this.administrators);
		this.entityManager.persist(other);
		this.testUser.getRoles().add(this.administrators);
		this.entityManager.flush();

		assertThat(this.service
			.users(new AdministrationService.UserQuery(null, null, null, null, null, null, null,
					this.administrators.getPublicId(), null, null, null), Pageable.unpaged())
			.getContent()).extracting(AppUser::getUsername).containsExactlyInAnyOrder("test-user", "other-user");
		assertThat(this.service
			.users(new AdministrationService.UserQuery(null, null, null, null, null, null, null,
					this.managers.getPublicId(), null, null, null), Pageable.unpaged())
			.getContent()).extracting(AppUser::getUsername).containsExactly("test-user");
	}

	@Test
	void filtersUsersByWhetherTheyArePrivileged() {
		this.testUser.getRoles().add(this.administrators);
		AppUser plain = new AppUser("plain-user", "Plain User", null);
		plain.getRoles().add(this.managers);
		this.entityManager.persist(plain);
		this.entityManager.flush();
		this.entityManager.clear();

		assertThat(this.service
			.users(new AdministrationService.UserQuery(null, null, null, null, null, null, null, null, true, null,
					null), Pageable.unpaged())
			.getContent()).extracting(AppUser::getUsername).containsExactly("test-user");
		assertThat(this.service
			.users(new AdministrationService.UserQuery(null, null, null, null, null, null, null, null, false, null,
					null), Pageable.unpaged())
			.getContent()).extracting(AppUser::getUsername).containsExactly("plain-user");
		assertThat(reload(this.testUser).isPrivileged()).isTrue();
	}

	@Test
	void rejectsADuplicateRoleName() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createRole(new AdminDtos.RoleRequest("Administrators", null)))
			.withMessage("Role name already exists.");
		assertThat(this.roles.count()).isEqualTo(3);
	}

	@Test
	void rejectsARoleWithAPermissionThatDoesNotExist() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.createRole(new AdminDtos.RoleRequest("New Role", Set.of(MISSING))))
			.withMessage("Permission was not found.");
		assertThat(this.roles.existsByName("New Role")).isFalse();
	}

	@Test
	void rejectsRenamingARoleToAnExistingName() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateRole(this.managers.getPublicId(),
					new AdminDtos.RoleRequest("Administrators", null)))
			.withMessage("Role name already exists.");
		assertThat(reload(this.managers).getName()).isEqualTo("Managers");
	}

	@Test
	void keepingARolesOwnNameIsNotAConflictAndARoleCanBeRenamed() {
		AppRole updated = this.service.updateRole(this.managers.getPublicId(),
				new AdminDtos.RoleRequest("Managers", Set.of(permission(Permissions.USER_READ).getPublicId())));
		AppRole renamed = this.service.updateRole(this.managers.getPublicId(),
				new AdminDtos.RoleRequest("Team Managers", Set.of(permission(Permissions.USER_READ).getPublicId())));

		assertThat(updated.getName()).isEqualTo("Team Managers");
		assertThat(renamed.getPermissions()).extracting(AppPermission::getName).containsExactly("user:read");
	}

	@Test
	void rejectsDeletingARoleThatHasUsers() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.deleteRole(this.managers.getPublicId()))
			.withMessage("Role has users.");
		assertThat(this.roles.existsById(this.managers.getId())).isTrue();
	}

	@Test
	void deletesARoleWithoutUsers() {
		this.service.deleteRole(this.administrators.getPublicId());
		this.entityManager.flush();

		assertThat(this.roles.existsById(this.administrators.getId())).isFalse();
	}

	@Test
	void anActorCannotGiveAUserARoleWithAPrivilegedPermissionTheyDoNotHold(CapturedOutput output) {
		authenticate("admin", Permissions.USER_ADD_ROLE);

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null,
					null, Set.of(this.administrators.getPublicId()))));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service
			.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User", "test@example.test",
					null, Set.of(this.managers.getPublicId(), this.administrators.getPublicId()))));

		assertThat(output).contains("\"exceeds_actor_privileges\"");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
		assertThat(reload(this.testUser).getRoles()).extracting(AppRole::getName).containsExactly("Managers");
	}

	@Test
	void anActorCanGrantAPrivilegedPermissionTheyHoldThemselves() {
		authenticate("admin", Permissions.USER_ADD_ROLE, Permissions.USER_CREATE);

		AppUser user = this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null, null,
				Set.of(this.administrators.getPublicId())));

		assertThat(user.getRoles()).extracting(AppRole::getName).containsExactly("Administrators");
		assertThat(user.isPrivileged()).isTrue();
	}

	@Test
	void anActorCanGrantNonPrivilegedPermissionsWithoutHoldingThem() {
		authenticate("admin", Permissions.USER_ADD_ROLE);

		AppUser user = this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null, null,
				Set.of(this.managers.getPublicId())));

		assertThat(user.getRoles()).extracting(AppRole::getName).containsExactly("Managers");
		assertThat(user.isPrivileged()).isFalse();
	}

	@Test
	void givingAUserARoleNeedsTheAddRolePermissionAndRemovingOneTheRemoveRolePermission(CapturedOutput output) {
		authenticate("admin", Permissions.USER_CREATE, Permissions.USER_UPDATE);

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null,
					null, Set.of(this.managers.getPublicId()))));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", null, Set.of(this.managers.getPublicId(), this.reviewers.getPublicId()))));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", null, Set.of(this.administrators.getPublicId()))));

		assertThat(output).contains("\"missing_permission\"");
		assertThat(reload(this.testUser).getRoles()).extracting(AppRole::getName).containsExactly("Managers");
	}

	@Test
	void changingAUsersDetailsNeedsTheUpdatePermission() {
		authenticate("admin", Permissions.USER_ADD_ROLE);

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Renamed",
						"test@example.test", null, Set.of(this.managers.getPublicId()))));

		authenticate("admin", Permissions.USER_UPDATE);
		this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Renamed",
				"test@example.test", null, Set.of(this.managers.getPublicId())));

		assertThat(reload(this.testUser).getName()).isEqualTo("Renamed");
	}

	@Test
	void anActorCanTakeARoleAwayWithOnlyTheRemoveRolePermission() {
		this.testUser.getRoles().add(this.reviewers);
		this.entityManager.flush();
		authenticate("admin", Permissions.USER_REMOVE_ROLE);

		this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
				"test@example.test", null, Set.of(this.managers.getPublicId())));

		assertThat(reload(this.testUser).getRoles()).extracting(AppRole::getName).containsExactly("Managers");
	}

	@Test
	void noUserCanHoldReviewingAndAPrivilegedPermissionTogether(CapturedOutput output) {
		this.testUser.getRoles().clear();
		this.testUser.getRoles().add(this.reviewers);
		this.entityManager.flush();

		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service
			.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User", "test@example.test",
					null, Set.of(this.reviewers.getPublicId(), this.administrators.getPublicId()))));
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null,
					null, Set.of(this.reviewers.getPublicId(), this.administrators.getPublicId()))))
			.withMessageContaining("review:decide")
			.withMessageContaining("user:create");

		assertThat(output).contains("\"separation_of_duties\"");
		assertThat(reload(this.testUser).getRoles()).extracting(AppRole::getName).containsExactly("Reviewers");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
	}

	@Test
	void aRoleCannotHoldReviewingAndAPrivilegedPermissionTogether() {
		Set<UUID> both = Set.of(permission(Permissions.REVIEW_DECIDE).getPublicId(),
				permission(Permissions.SETTINGS_UPDATE).getPublicId());

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.createRole(new AdminDtos.RoleRequest("Mixed", both)));
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateRole(this.reviewers.getPublicId(),
					new AdminDtos.RoleRequest("Reviewers", Set.of(permission(Permissions.REVIEW_DECIDE).getPublicId(),
							permission(Permissions.SETTINGS_UPDATE).getPublicId()))));
		assertThat(this.roles.existsByName("Mixed")).isFalse();
	}

	@Test
	void givingARoleAPermissionIsRejectedWhenAUserOfTheRoleHoldsAConflictingOneThroughAnotherRole() {
		this.testUser.getRoles().add(this.reviewers);
		this.entityManager.flush();
		this.entityManager.clear();

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.updateRole(this.managers.getPublicId(),
					new AdminDtos.RoleRequest("Managers", Set.of(permission(Permissions.USER_READ).getPublicId(),
							permission(Permissions.USER_CREATE).getPublicId()))));
	}

	@Test
	void anActorCannotChangeTheirOwnAccessOrSuspendOrRemoveThemselves(CapturedOutput output) {
		authenticate("test-user", Permissions.USER_ADD_ROLE, Permissions.USER_REMOVE_ROLE, Permissions.USER_SUSPEND,
				Permissions.USER_REMOVE);

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.OTHER, null));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(
				() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", null, Set.of(this.administrators.getPublicId()))));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.lifecycle.remove(this.testUser.getPublicId(), ReasonCode.OTHER, null));

		assertThat(output).contains("\"self_modification\"");
		assertThat(sessionOf("test-user").isExpired()).isFalse();
	}

	@Test
	void anActorCanChangeTheirOwnNameAndEmail() {
		authenticate("test-user", Permissions.USER_UPDATE);

		this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Renamed User",
				"renamed@example.test", null, Set.of(this.managers.getPublicId())));

		assertThat(reload(this.testUser).getName()).isEqualTo("Renamed User");
	}

	@Test
	void anActorCannotGiveARolePrivilegedPermissionsTheyDoNotHold(CapturedOutput output) {
		authenticate("admin", Permissions.ROLE_ADD_PERMISSION);
		Set<UUID> privileged = Set.of(permission(Permissions.USER_CREATE).getPublicId());

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.createRole(new AdminDtos.RoleRequest("Creators", privileged)));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.updateRole(this.managers.getPublicId(),
					new AdminDtos.RoleRequest("Managers", Set.of(permission(Permissions.USER_READ).getPublicId(),
							permission(Permissions.USER_CREATE).getPublicId()))));

		assertThat(output).contains("\"exceeds_actor_privileges\"");
		assertThat(reload(this.managers).getPermissions()).extracting(AppPermission::getName)
			.containsExactly("user:read");
	}

	@Test
	void givingARolePermissionsNeedsTheAddPermissionAndTakingThemAwayTheRemovePermission(CapturedOutput output) {
		authenticate("admin", Permissions.ROLE_CREATE, Permissions.ROLE_UPDATE);

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service
			.createRole(new AdminDtos.RoleRequest("Readers", Set.of(permission(Permissions.USER_READ).getPublicId()))));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service
			.updateRole(this.managers.getPublicId(), new AdminDtos.RoleRequest("Managers", null)));

		assertThat(output).contains("\"missing_permission\"");
		assertThat(reload(this.managers).getPermissions()).extracting(AppPermission::getName)
			.containsExactly("user:read");
	}

	@Test
	void anActorCanKeepAPermissionTheyDoNotHoldOnARoleTheyRename() {
		authenticate("admin", Permissions.ROLE_UPDATE);

		AppRole role = this.service.updateRole(this.administrators.getPublicId(),
				new AdminDtos.RoleRequest("Creators", Set.of(permission(Permissions.USER_CREATE).getPublicId())));

		assertThat(role.getName()).isEqualTo("Creators");
		assertThat(role.getPermissions()).extracting(AppPermission::getName).containsExactly("user:create");
	}

	@Test
	void revokesOneUsersSessionsWithoutChangingTheirAccount(CapturedOutput output) {
		this.service.revokeSessions(this.testUser.getPublicId());

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(reload(this.testUser).isSuspended()).isFalse();
		assertThat(output).contains("\"administrative_revocation\"");
		assertThat(this.auditTrail.find(AuditQuery.where().action("revoke_sessions"))).singleElement()
			.satisfies(event -> assertThat(event.target().name()).isEqualTo("test-user"));
	}

	@Test
	void revokesEveryUsersSessionsExceptTheCallers() {
		AppUser admin = new AppUser("admin", "Administrator", null);
		admin.getRoles().add(this.managers);
		this.entityManager.persist(admin);
		signIn("session-2", "admin");
		authenticate("admin", Permissions.USER_REVOKE_SESSION);

		this.service.revokeAllSessions();

		assertThat(sessionOf("test-user").isExpired()).isTrue();
		assertThat(sessionOf("admin").isExpired()).isFalse();
	}

	@Test
	void theSeededPermissionsAreTheOnesTheCodeChecks() throws Exception {
		Set<String> constants = new java.util.HashSet<>();
		for (java.lang.reflect.Field field : Permissions.class.getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isPublic(field.getModifiers())) {
				constants.add((String) field.get(null));
			}
		}

		assertThat(this.permissions.findAll()).extracting(AppPermission::getName)
			.containsExactlyInAnyOrderElementsOf(constants);
		assertThat(this.permissions.findAll()).filteredOn(AppPermission::isPrivileged)
			.extracting(AppPermission::getName)
			.containsExactlyInAnyOrder("user:create", "user:add-role", "user:unsuspend", "role:add-permission",
					"settings:update");
		AppPermission decide = permission(Permissions.REVIEW_DECIDE);
		assertThat(this.permissions.findAll()).filteredOn(AppPermission::isPrivileged)
			.allSatisfy(privileged -> assertThat(decide.conflictsWith(privileged)).isTrue());
	}

	private AppPermission permission(String name) {
		return this.permissions.findAll()
			.stream()
			.filter(permission -> permission.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private void authenticate(String username, String... authorities) {
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(username, null, authorities));
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
