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

import com.example.commons.accounts.admin.AdministrationAuditLogger;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionRevocationService;

@AccountsJpaTest
class DormantUserDisablerTest {

	private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

	private static final Duration THRESHOLD = Duration.ofDays(90);

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);

	private AppGroup group;

	@BeforeEach
	void createGroup() {
		this.group = this.entityManager.persist(new AppGroup("users"));
	}

	@Test
	void disablesAUserWithNoActivityForLongerThanTheThresholdAndEndsTheirSessions() {
		user("stale", true, days(200), days(200), null);

		assertThat(disabler().disableDormantUsers()).isEqualTo(1);

		assertThat(reload("stale").isEnabled()).isFalse();
		verify(this.sessionRevocationService).revoke("stale", "dormant_account");
	}

	@Test
	void disablesAUserWhoLastSignedInLongAgo() {
		user("lapsed", true, days(400), days(400), days(120));

		assertThat(disabler().disableDormantUsers()).isEqualTo(1);

		assertThat(reload("lapsed").isEnabled()).isFalse();
	}

	@Test
	void leavesAUserWhoSignedInWithinTheThreshold() {
		user("recent", true, days(400), days(400), days(10));

		assertThat(disabler().disableDormantUsers()).isZero();

		assertThat(reload("recent").isEnabled()).isTrue();
		verifyNoMoreInteractions(this.sessionRevocationService);
	}

	@Test
	void leavesAUserWhoWasCreatedOrChangedByAnAdministratorWithinTheThreshold() {
		user("new", true, days(10), days(10), null);
		user("re-enabled", true, days(400), days(5), days(300));

		assertThat(disabler().disableDormantUsers()).isZero();

		assertThat(reload("new").isEnabled()).isTrue();
		assertThat(reload("re-enabled").isEnabled()).isTrue();
	}

	@Test
	void ignoresAUserWhoIsAlreadyDisabled() {
		user("disabled", false, days(400), days(400), null);

		assertThat(disabler().disableDormantUsers()).isZero();

		verifyNoMoreInteractions(this.sessionRevocationService);
	}

	private DormantUserDisabler disabler() {
		return new DormantUserDisabler(this.users, this.sessionRevocationService, new AdministrationAuditLogger(),
				THRESHOLD, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static Instant days(int daysAgo) {
		return NOW.minus(Duration.ofDays(daysAgo));
	}

	/**
	 * Persists a user, then backdates its timestamps with a bulk update, which bypasses
	 * the lifecycle callbacks that would stamp the current time.
	 */
	private void user(String username, boolean enabled, Instant createdAt, Instant updatedAt, Instant lastLoginAt) {
		AppUser user = new AppUser(username, username, null, enabled);
		user.getGroups().add(this.group);
		this.entityManager.persist(user);
		this.entityManager.flush();
		this.entityManager.getEntityManager()
			.createQuery("update AppUser u set u.createdAt = :created, u.updatedAt = :updated, "
					+ "u.lastLoginAt = :login where u.username = :username")
			.setParameter("created", createdAt)
			.setParameter("updated", updatedAt)
			.setParameter("login", lastLoginAt)
			.setParameter("username", username)
			.executeUpdate();
		this.entityManager.clear();
	}

	private AppUser reload(String username) {
		this.entityManager.flush();
		this.entityManager.clear();
		return this.users.findByUsernameAndEnabledTrue(username)
			.orElseGet(() -> this.users.findAll()
				.stream()
				.filter(u -> u.getUsername().equals(username))
				.findFirst()
				.orElseThrow());
	}

}
