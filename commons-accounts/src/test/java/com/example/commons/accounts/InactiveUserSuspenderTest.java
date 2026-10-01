package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppSettingRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.settings.SettingsService;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Tests {@link InactiveUserSuspender} against the real schema, with the default settings
 * the migration seeds: suspend after 90 days, remove after 180.
 */
@AccountsJpaTest
class InactiveUserSuspenderTest {

	private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppSettingRepository settings;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);

	private AppGroup group;

	@BeforeEach
	void createGroup() {
		this.group = this.entityManager.persist(new AppGroup("users"));
	}

	@Test
	void suspendsAnActiveUserNotInUseForLongerThanTheSuspensionThresholdAndEndsTheirSessions() {
		user("stale", days(100), null, ReasonCode.INACTIVE_ACCOUNT, false);

		suspender().run();

		AppUser stale = reload("stale");
		assertThat(stale.isSuspended()).isTrue();
		assertThat(stale.getSuspensionReasonCode()).isEqualTo("inactive_account");
		assertThat(stale.getSuspendedAt()).isEqualTo(NOW);
		verify(this.sessionRevocationService).revoke("stale", "account_suspended");
	}

	@Test
	void leavesAUserWhoSignedInWithinTheThresholdWhateverTheirAge() {
		user("recent", days(400), days(10), null, false);

		suspender().run();

		assertThat(reload("recent").isSuspended()).isFalse();
		verifyNoMoreInteractions(this.sessionRevocationService);
	}

	@Test
	void leavesAUserWhoWasCreatedOrUnsuspendedWithinTheThresholdDespiteAnOldSignIn() {
		user("unsuspended", days(5), days(300), null, false);
		user("new", days(10), null, null, false);

		suspender().run();

		assertThat(reload("unsuspended").isSuspended()).isFalse();
		assertThat(reload("new").isSuspended()).isFalse();
	}

	@Test
	void removesAnAccountNotInUseForLongerThanTheRemovalThreshold() {
		user("gone", days(200), null, null, false);

		suspender().run();

		assertThat(this.users.existsByUsername("gone")).isFalse();
		verify(this.sessionRevocationService).revoke("gone", "account_deleted");
	}

	@Test
	void removesAnAccountSuspendedByAPersonOnceItIsPastTheRemovalThreshold() {
		user("manual", days(200), null, ReasonCode.OTHER, true);
		user("still-suspended", days(120), null, ReasonCode.OTHER, true);

		suspender().run();

		assertThat(this.users.existsByUsername("manual")).isFalse();
		assertThat(reload("still-suspended").isSuspended()).isTrue();
	}

	@Test
	void doesNothingWhileTheInactivitySettingIsOff() {
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = 'false' WHERE name = 'inactivity.enabled'");
		user("stale", days(200), null, null, false);

		suspender().run();

		assertThat(this.users.existsByUsername("stale")).isTrue();
		verifyNoMoreInteractions(this.sessionRevocationService);
	}

	private InactiveUserSuspender suspender() {
		AccountAuditLogger auditLogger = new AccountAuditLogger();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccountLifecycleService lifecycle = new AccountLifecycleService(this.users, this.sessionRevocationService,
				auditLogger, null, null, clock);
		return new InactiveUserSuspender(this.users, lifecycle, new SettingsService(this.settings, auditLogger), clock);
	}

	private static Instant days(int daysAgo) {
		return NOW.minus(Duration.ofDays(daysAgo));
	}

	/**
	 * Persists a user, then backdates its inactivity clock and last sign-in with a bulk
	 * update, which bypasses the lifecycle callbacks that would stamp the current time.
	 */
	private void user(String username, Instant clockStartedAt, Instant lastLoginAt, ReasonCode suspendedFor,
			boolean suspended) {
		AppUser user = new AppUser(username, username, null);
		user.getGroups().add(this.group);
		if (suspended) {
			user.suspend(clockStartedAt, suspendedFor, null);
		}
		this.entityManager.persist(user);
		this.entityManager.flush();
		this.entityManager.getEntityManager()
			.createQuery("update AppUser u set u.inactivityClockStartedAt = :clock, u.lastLoginAt = :login "
					+ "where u.username = :username")
			.setParameter("clock", clockStartedAt)
			.setParameter("login", lastLoginAt)
			.setParameter("username", username)
			.executeUpdate();
		this.entityManager.clear();
	}

	private AppUser reload(String username) {
		this.entityManager.flush();
		this.entityManager.clear();
		return this.users.findAll().stream().filter(u -> u.getUsername().equals(username)).findFirst().orElseThrow();
	}

}
