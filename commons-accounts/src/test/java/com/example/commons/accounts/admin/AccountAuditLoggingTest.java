package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.admin.AdministrationServiceTest.InMemorySessionRepository;
import com.example.commons.accounts.audit.AccountAudit;
import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppPermissionRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.audit.AuditOutcome;
import com.example.commons.audit.AuditQuery;
import com.example.commons.audit.AuditRecord;
import com.example.commons.audit.AuditTrail;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;

/**
 * Tests the account audit events, as audit trail rows and log events, and the created-by
 * and updated-by actors against the real schema. Each change runs in a transaction that
 * really commits, because a successful change is only logged after its commit; the test's
 * own transaction is therefore disabled and the tables are emptied after each test.
 */
@AccountsJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountAuditLoggingTest {

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppRoleRepository roles;

	@Autowired
	private AppPermissionRepository permissions;

	@Autowired
	private AuditTrail auditTrail;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final Logger logger = (Logger) LoggerFactory.getLogger(AuditTrail.class);

	private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

	private TransactionTemplate transaction;

	private AdministrationService service;

	private AccountLifecycleService lifecycle;

	private AppRole managers;

	private AppRole administrators;

	private AppRole creators;

	private AppUser testUser;

	@BeforeEach
	void setUp() {
		this.transaction = new TransactionTemplate(this.transactionManager);
		SessionRevocationService revocation = new SessionRevocationService(new SessionRegistryImpl(),
				new InMemorySessionRepository(), new SessionLifecycleAuditLogger());
		AccountAudit audit = new AccountAudit(this.auditTrail);
		this.service = new AdministrationService(this.users, this.roles, this.permissions, revocation, audit);
		this.lifecycle = new AccountLifecycleService(this.users, revocation, audit, null, null, Clock.systemUTC());
		inTransaction(() -> {
			AppRole managers = new AppRole("Managers");
			managers.getPermissions().add(permission(Permissions.USER_READ));
			this.managers = this.roles.save(managers);
			AppRole administrators = new AppRole("Administrators");
			administrators.getPermissions().add(permission(Permissions.ROLE_READ));
			this.administrators = this.roles.save(administrators);
			AppRole creators = new AppRole("Creators");
			creators.getPermissions().add(permission(Permissions.USER_CREATE));
			this.creators = this.roles.save(creators);
			AppUser testUser = new AppUser("test-user", "Test User", "test@example.test");
			testUser.getRoles().add(this.managers);
			this.testUser = this.users.save(testUser);
			return null;
		});
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("admin", null, Permissions.USER_CREATE,
					Permissions.USER_ADD_ROLE, Permissions.USER_REMOVE_ROLE, Permissions.USER_UPDATE,
					Permissions.ROLE_UPDATE, Permissions.ROLE_ADD_PERMISSION, Permissions.ROLE_REMOVE_PERMISSION,
					Permissions.ROLE_DELETE));
		this.logEvents.start();
		this.logger.addAppender(this.logEvents);
	}

	@AfterEach
	void tearDown() {
		this.logger.detachAppender(this.logEvents);
		this.logEvents.stop();
		SecurityContextHolder.clearContext();
		inTransaction(() -> {
			this.jdbcTemplate.update("DELETE FROM audit_event");
			this.users.deleteAll();
			this.roles.deleteAll();
			return null;
		});
	}

	@Test
	void recordsSystemAsTheActorOfAChangeMadeWithoutAnAuthenticatedUser() {
		assertThat(this.testUser.getCreatedBy()).isEqualTo("system");
		assertThat(this.testUser.getUpdatedBy()).isEqualTo("system");
	}

	@Test
	void logsACreatedUserWithTheAccessItIsGrantedAndRecordsTheActor() {
		AppUser user = inTransaction(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user",
				"New User", "new@example.test", null, Set.of(this.managers.getPublicId()))));

		assertThat(user.getCreatedBy()).isEqualTo("admin");
		assertThat(user.getUpdatedBy()).isEqualTo("admin");
		assertThat(logged()).containsOnlyOnce("event.action=\"create_user\"")
			.contains("event.category=\"[iam]\"")
			.contains("event.type=\"[user, creation]\"")
			.contains("event.outcome=\"success\"")
			.contains("user.name=\"admin\"")
			.contains("user.target.id=\"" + user.getPublicId() + "\"")
			.contains("user.target.name=\"new-user\"")
			.contains("user.target.status=\"active\"")
			.contains("user.target.role.name=\"[Managers]\"")
			.contains("user.target.permissions=\"[user:read]\"")
			.contains("user.target.privileged=\"false\"")
			.contains("roles.added=\"[Managers]\"")
			.contains("permissions.added=\"[user:read]\"")
			.contains("related.user=\"[admin, new-user]\"")
			.doesNotContain("new@example.test")
			.doesNotContain("New User");
	}

	@Test
	void doesNotLogAChangeThatIsRolledBack() {
		this.transaction.executeWithoutResult(status -> {
			this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null, null,
					Set.of(this.managers.getPublicId())));
			status.setRollbackOnly();
		});

		assertThat(logged()).doesNotContain("create_user");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
	}

	@Test
	void logsAnUpdatedUserWithItsPriorStateOnlyTheChangesAndNoPersonalDataValues() {
		AppUser user = inTransaction(
				() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
						"changed@example.test", null, Set.of(this.administrators.getPublicId()))));

		assertThat(user.getUpdatedBy()).isEqualTo("admin");
		assertThat(user.getCreatedBy()).isEqualTo("system");
		assertThat(logged()).containsOnlyOnce("event.action=\"update_user\"")
			.contains("event.type=\"[user, change]\"")
			.contains("user.target.status=\"active\"")
			.contains("user.target.role.name=\"[Managers]\"")
			.contains("user.target.permissions=\"[user:read]\"")
			.contains("user.changes.role.name=\"[Administrators]\"")
			.contains("user.changes.permissions=\"[role:read]\"")
			.contains("user.changes.fields=\"[email]\"")
			.contains("roles.added=\"[Administrators]\"")
			.contains("roles.removed=\"[Managers]\"")
			.contains("permissions.added=\"[role:read]\"")
			.contains("permissions.removed=\"[user:read]\"")
			.contains("related.user=\"[admin, test-user]\"")
			.doesNotContain("user.changes.privileged")
			.doesNotContain("changed@example.test")
			.doesNotContain("test@example.test");
	}

	@Test
	void logsWhenAUserBecomesPrivileged() {
		inTransaction(
				() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest("Test User",
						"test@example.test", null, Set.of(this.managers.getPublicId(), this.creators.getPublicId()))));

		assertThat(logged()).containsOnlyOnce("event.action=\"update_user\"")
			.contains("user.target.privileged=\"false\"")
			.contains("user.changes.privileged=\"true\"");
		assertThat(events()).singleElement()
			.satisfies(event -> assertThat(event.details(Map.class)).containsEntry("privileged", true));
	}

	@Test
	void omitsTheChangesOfValuesThatDidNotChange() {
		inTransaction(() -> this.service.updateUser(this.testUser.getPublicId(), new AdminDtos.UserUpdateRequest(
				"Renamed User", "test@example.test", null, Set.of(this.managers.getPublicId()))));

		assertThat(logged()).containsOnlyOnce("event.action=\"update_user\"")
			.contains("user.changes.fields=\"[full_name]\"")
			.contains("permissions.added=\"[]\"")
			.contains("permissions.removed=\"[]\"")
			.doesNotContain("user.changes.status")
			.doesNotContain("user.changes.role.name")
			.doesNotContain("user.changes.permissions")
			.doesNotContain("Renamed User");
	}

	@Test
	void logsARemovedUserWithTheReasonAndTheAccessItLoses() {
		inTransaction(() -> {
			this.lifecycle.remove(this.testUser.getPublicId(), ReasonCode.LEFT_ORGANISATION, "moved teams");
			return null;
		});

		assertThat(logged()).containsOnlyOnce("event.action=\"delete_user\"")
			.contains("event.type=\"[user, deletion]\"")
			.contains("event.reason=\"left_organisation\"")
			.doesNotContain("moved teams")
			.contains("user.target.name=\"test-user\"")
			.contains("roles.removed=\"[Managers]\"")
			.contains("permissions.removed=\"[user:read]\"");
	}

	@Test
	void logsASuspensionAndAnUnsuspension() {
		inTransaction(() -> this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.POLICY_VIOLATION, null));
		inTransaction(() -> this.lifecycle.unsuspend(this.testUser.getPublicId()));

		assertThat(logged()).contains("event.action=\"suspend_user\"")
			.contains("event.reason=\"policy_violation\"")
			.contains("user.changes.status=\"suspended\"")
			.contains("event.action=\"unsuspend_user\"")
			.contains("user.changes.status=\"active\"");
	}

	@Test
	void appendsEachSuccessfulChangeToTheAuditTrailWithoutAnEmailAddress() {
		inTransaction(() -> this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User",
				"new@example.test", null, Set.of(this.managers.getPublicId()))));
		inTransaction(
				() -> this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.POLICY_VIOLATION, "see ticket"));
		inTransaction(() -> {
			this.lifecycle.remove(this.testUser.getPublicId(), ReasonCode.LEFT_ORGANISATION, null);
			return null;
		});

		List<AuditRecord> events = events();
		assertThat(events).extracting(AuditRecord::action)
			.containsExactly("create_user", "suspend_user", "delete_user");
		assertThat(events).extracting(AuditRecord::actor).containsOnly("admin");
		assertThat(events).extracting(event -> event.target().type()).containsOnly("USER");
		AuditRecord removal = events.get(2);
		assertThat(removal.target().name()).isEqualTo("test-user");
		assertThat(removal.target().fullName()).isEqualTo("Test User");
		assertThat(removal.reasonCode()).isEqualTo("left_organisation");
		assertThat(events.get(1).reasonNote()).isEqualTo("see ticket");
		assertThat(events).extracting(event -> String.valueOf(event.details(Object.class)))
			.noneMatch(details -> details.contains("@"));
	}

	@Test
	void doesNotAppendAChangeThatIsRolledBackToTheAuditTrail() {
		this.transaction.executeWithoutResult(status -> {
			this.service.createUser(new AdminDtos.UserCreateRequest("new-user", "New User", null, null,
					Set.of(this.managers.getPublicId())));
			status.setRollbackOnly();
		});

		assertThat(events()).isEmpty();
	}

	@Test
	void logsARolePermissionChangeWithTheNumberOfUsersItAffects() {
		AppRole role = inTransaction(() -> this.service.updateRole(this.managers.getPublicId(),
				new AdminDtos.RoleRequest("Managers", Set.of(permission(Permissions.ROLE_READ).getPublicId()))));

		assertThat(role.getUpdatedBy()).isEqualTo("admin");
		assertThat(logged()).containsOnlyOnce("event.action=\"update_role\"")
			.contains("event.type=\"[group, change]\"")
			.contains("role.id=\"" + this.managers.getPublicId() + "\"")
			.contains("role.name=\"Managers\"")
			.contains("role.permissions=\"[user:read]\"")
			.contains("role.changes.permissions=\"[role:read]\"")
			.contains("role.affected_user_count=\"1\"")
			.contains("permissions.added=\"[role:read]\"")
			.contains("permissions.removed=\"[user:read]\"")
			.contains("related.user=\"[admin]\"")
			.doesNotContain("role.changes.name");
	}

	@Test
	void logsARejectedChangeImmediatelyWithItsReason() {
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> inTransaction(() -> this.service.createUser(new AdminDtos.UserCreateRequest("test-user",
					"Test User", null, null, Set.of(this.managers.getPublicId())))));
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> inTransaction(() -> this.service
			.updateRole(this.managers.getPublicId(), new AdminDtos.RoleRequest("Administrators", null))));
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> inTransaction(() -> {
			this.service.deleteRole(this.managers.getPublicId());
			return null;
		}));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> inTransaction(
				() -> this.service.updateRole(this.administrators.getPublicId(), new AdminDtos.RoleRequest(
						"Administrators", Set.of(permission(Permissions.SETTINGS_UPDATE).getPublicId())))));

		assertThat(logged()).contains("event.action=\"create_user\"")
			.contains("event.reason=\"username_exists\"")
			.contains("event.action=\"update_role\"")
			.contains("role.changes.name=\"Administrators\"")
			.contains("event.reason=\"name_exists\"")
			.contains("event.action=\"delete_role\"")
			.contains("event.reason=\"role_has_users\"")
			.contains("event.reason=\"exceeds_actor_privileges\"")
			.contains("event.outcome=\"failure\"")
			.doesNotContain("event.outcome=\"success\"")
			.doesNotContain("INFO");
		assertThat(events()).extracting(AuditRecord::action, AuditRecord::outcome, AuditRecord::reasonCode)
			.containsExactly(tuple("create_user", AuditOutcome.FAILURE, "username_exists"),
					tuple("update_role", AuditOutcome.FAILURE, "name_exists"),
					tuple("delete_role", AuditOutcome.FAILURE, "role_has_users"),
					tuple("update_role", AuditOutcome.FAILURE, "exceeds_actor_privileges"));
	}

	@Test
	void recordsARefusalOfAChangeToTheActorsOwnAccount() {
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("test-user", null, Permissions.USER_SUSPEND));

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> inTransaction(
				() -> this.lifecycle.suspend(this.testUser.getPublicId(), ReasonCode.POLICY_VIOLATION, null)));

		assertThat(events()).singleElement().satisfies(event -> {
			assertThat(event.action()).isEqualTo("suspend_user");
			assertThat(event.outcome()).isEqualTo(AuditOutcome.FAILURE);
			assertThat(event.reasonCode()).isEqualTo("self_modification");
		});
		assertThat(logged()).contains("WARN").contains("event.action=\"suspend_user\"");
	}

	/**
	 * Returns every audit trail event, refusals included, oldest first.
	 */
	private List<AuditRecord> events() {
		return this.auditTrail.find(AuditQuery.where().anyOutcome());
	}

	private AppPermission permission(String name) {
		return this.permissions.findAll()
			.stream()
			.filter(permission -> permission.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	/**
	 * Returns every administration audit event logged, one per line, with its level and
	 * key-value pairs.
	 */
	private String logged() {
		return this.logEvents.list.stream()
			.map(event -> event.getLevel() + " " + event.getKeyValuePairs())
			.collect(Collectors.joining("\n"));
	}

	private <T> T inTransaction(Supplier<T> action) {
		return this.transaction.execute(status -> action.get());
	}

}
