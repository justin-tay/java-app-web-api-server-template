package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.admin.AdministrationServiceTest.InMemorySessionRepository;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;

/**
 * Tests the administration audit events and the created-by and updated-by actors against
 * the real schema. Each change runs in a transaction that really commits, because a
 * successful change is only logged after its commit; the test's own transaction is
 * therefore disabled and the tables are emptied after each test.
 */
@AccountsJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdministrationAuditLoggingTest {

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppGroupRepository groups;

	@Autowired
	private AppRoleRepository roles;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final Logger logger = (Logger) LoggerFactory.getLogger(AdministrationAuditLogger.class);

	private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

	private TransactionTemplate transaction;

	private AdministrationService service;

	private AppRole userManage;

	private AppRole groupManage;

	private AppGroup managers;

	private AppGroup administrators;

	private AppUser testUser;

	@BeforeEach
	void setUp() {
		this.transaction = new TransactionTemplate(this.transactionManager);
		this.service = new AdministrationService(
				this.users, this.groups, this.roles, new SessionRevocationService(new SessionRegistryImpl(),
						new InMemorySessionRepository(), new SessionLifecycleAuditLogger()),
				new AdministrationAuditLogger());
		inTransaction(() -> {
			this.userManage = this.roles.save(new AppRole("USER_MANAGE"));
			this.groupManage = this.roles.save(new AppRole("GROUP_MANAGE"));
			AppGroup managers = new AppGroup("Managers");
			managers.getRoles().add(this.userManage);
			this.managers = this.groups.save(managers);
			AppGroup administrators = new AppGroup("Administrators");
			administrators.getRoles().add(this.groupManage);
			this.administrators = this.groups.save(administrators);
			AppUser testUser = new AppUser("test-user", "Test User", "test@example.test", true);
			testUser.getGroups().add(this.managers);
			this.testUser = this.users.save(testUser);
			return null;
		});
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("admin", null, "ROLE_USER_MANAGE", "ROLE_GROUP_MANAGE"));
		this.logEvents.start();
		this.logger.addAppender(this.logEvents);
	}

	@AfterEach
	void tearDown() {
		this.logger.detachAppender(this.logEvents);
		this.logEvents.stop();
		SecurityContextHolder.clearContext();
		inTransaction(() -> {
			this.users.deleteAll();
			this.groups.deleteAll();
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
				"New User", "new@example.test", true, Set.of(this.managers.getId()))));

		assertThat(user.getCreatedBy()).isEqualTo("admin");
		assertThat(user.getUpdatedBy()).isEqualTo("admin");
		assertThat(logged()).containsOnlyOnce("event.action=\"create_user\"")
			.contains("event.category=\"[iam]\"")
			.contains("event.type=\"[user, creation]\"")
			.contains("event.outcome=\"success\"")
			.contains("user.name=\"admin\"")
			.contains("user.target.id=\"" + user.getId() + "\"")
			.contains("user.target.name=\"new-user\"")
			.contains("user.target.enabled=\"true\"")
			.contains("user.target.group.name=\"[Managers]\"")
			.contains("user.target.roles=\"[USER_MANAGE]\"")
			.contains("groups.added=\"[Managers]\"")
			.contains("roles.added=\"[USER_MANAGE]\"")
			.contains("related.user=\"[admin, new-user]\"")
			.doesNotContain("new@example.test")
			.doesNotContain("New User");
	}

	@Test
	void doesNotLogAChangeThatIsRolledBack() {
		this.transaction.executeWithoutResult(status -> {
			this.service.createUser(
					new AdminDtos.UserCreateRequest("new-user", "New User", null, true, Set.of(this.managers.getId())));
			status.setRollbackOnly();
		});

		assertThat(logged()).doesNotContain("create_user");
		assertThat(this.users.existsByUsername("new-user")).isFalse();
	}

	@Test
	void logsAnUpdatedUserWithItsPriorStateOnlyTheChangesAndNoPersonalDataValues() {
		AppUser user = inTransaction(
				() -> this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Test User",
						"changed@example.test", false, Set.of(this.administrators.getId()))));

		assertThat(user.getUpdatedBy()).isEqualTo("admin");
		assertThat(user.getCreatedBy()).isEqualTo("system");
		assertThat(logged()).containsOnlyOnce("event.action=\"update_user\"")
			.contains("event.type=\"[user, change]\"")
			.contains("user.target.enabled=\"true\"")
			.contains("user.target.group.name=\"[Managers]\"")
			.contains("user.target.roles=\"[USER_MANAGE]\"")
			.contains("user.changes.enabled=\"false\"")
			.contains("user.changes.group.name=\"[Administrators]\"")
			.contains("user.changes.roles=\"[GROUP_MANAGE]\"")
			.contains("user.changes.fields=\"[email]\"")
			.contains("groups.added=\"[Administrators]\"")
			.contains("groups.removed=\"[Managers]\"")
			.contains("roles.added=\"[GROUP_MANAGE]\"")
			.contains("roles.removed=\"[USER_MANAGE]\"")
			.contains("related.user=\"[admin, test-user]\"")
			.doesNotContain("changed@example.test")
			.doesNotContain("test@example.test");
	}

	@Test
	void omitsTheChangesOfValuesThatDidNotChange() {
		inTransaction(
				() -> this.service.updateUser(this.testUser.getId(), new AdminDtos.UserUpdateRequest("Renamed User",
						"test@example.test", true, Set.of(this.managers.getId()))));

		assertThat(logged()).containsOnlyOnce("event.action=\"update_user\"")
			.contains("user.changes.fields=\"[full_name]\"")
			.contains("roles.added=\"[]\"")
			.contains("roles.removed=\"[]\"")
			.doesNotContain("user.changes.enabled")
			.doesNotContain("user.changes.group.name")
			.doesNotContain("user.changes.roles")
			.doesNotContain("Renamed User");
	}

	@Test
	void logsADeletedUserWithTheAccessItLoses() {
		inTransaction(() -> {
			this.service.deleteUser(this.testUser.getId());
			return null;
		});

		assertThat(logged()).containsOnlyOnce("event.action=\"delete_user\"")
			.contains("event.type=\"[user, deletion]\"")
			.contains("user.target.name=\"test-user\"")
			.contains("groups.removed=\"[Managers]\"")
			.contains("roles.removed=\"[USER_MANAGE]\"");
	}

	@Test
	void logsAGroupRoleChangeWithTheNumberOfUsersItAffects() {
		AppGroup group = inTransaction(() -> this.service.updateGroup(this.managers.getId(),
				new AdminDtos.GroupRequest("Managers", Set.of(this.groupManage.getId()))));

		assertThat(group.getUpdatedBy()).isEqualTo("admin");
		assertThat(logged()).containsOnlyOnce("event.action=\"update_group\"")
			.contains("event.type=\"[group, change]\"")
			.contains("group.id=\"" + this.managers.getId() + "\"")
			.contains("group.name=\"Managers\"")
			.contains("group.roles=\"[USER_MANAGE]\"")
			.contains("group.changes.roles=\"[GROUP_MANAGE]\"")
			.contains("group.affected_user_count=\"1\"")
			.contains("roles.added=\"[GROUP_MANAGE]\"")
			.contains("roles.removed=\"[USER_MANAGE]\"")
			.contains("related.user=\"[admin]\"")
			.doesNotContain("group.changes.name");
	}

	@Test
	void logsARejectedChangeImmediatelyWithItsReason() {
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> inTransaction(() -> this.service.createUser(
				new AdminDtos.UserCreateRequest("test-user", "Test User", null, true, Set.of(this.managers.getId())))));
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> inTransaction(() -> this.service
			.updateGroup(this.managers.getId(), new AdminDtos.GroupRequest("Administrators", null))));
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> inTransaction(() -> {
			this.service.deleteGroup(this.managers.getId());
			return null;
		}));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> inTransaction(() -> {
			this.service.deleteRole(this.userManage.getId());
			return null;
		}));

		assertThat(logged()).contains("event.action=\"create_user\"")
			.contains("event.reason=\"username_exists\"")
			.contains("event.action=\"update_group\"")
			.contains("group.changes.name=\"Administrators\"")
			.contains("event.reason=\"name_exists\"")
			.contains("event.action=\"delete_group\"")
			.contains("event.reason=\"group_has_users\"")
			.contains("event.action=\"delete_role\"")
			.contains("event.reason=\"reserved_role\"")
			.contains("event.outcome=\"failure\"")
			.doesNotContain("event.outcome=\"success\"")
			.doesNotContain("INFO");
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
