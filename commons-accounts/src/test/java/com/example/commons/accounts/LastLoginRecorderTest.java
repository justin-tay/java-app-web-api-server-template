package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;

import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.UserStatus;

@AccountsJpaTest
class LastLoginRecorderTest {

	private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Test
	void recordsTheSignInTimeAndMakesAPendingUserActiveWithoutTouchingAuditColumns() {
		AppUser alice = new AppUser("alice", "Alice", null, true);
		alice.getGroups().add(this.entityManager.persist(new AppGroup("users")));
		this.entityManager.persist(alice);
		this.entityManager.flush();
		this.entityManager.clear();
		assertThat(UserStatus.of(this.users.findById(alice.getId()).orElseThrow())).isEqualTo(UserStatus.PENDING);
		Instant updatedAt = this.users.findById(alice.getId()).orElseThrow().getUpdatedAt();
		this.entityManager.clear();

		new LastLoginRecorder(this.users, Clock.fixed(NOW, ZoneOffset.UTC)).onAuthenticationSuccess(
				new InteractiveAuthenticationSuccessEvent(new TestingAuthenticationToken("alice", "n/a"), getClass()));
		this.entityManager.clear();

		AppUser reloaded = this.users.findById(alice.getId()).orElseThrow();
		assertThat(reloaded.getLastLoginAt()).isEqualTo(NOW);
		assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
		assertThat(UserStatus.of(reloaded)).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void aDisabledUserIsDisabledWhetherOrNotTheyHaveSignedIn() {
		assertThat(UserStatus.of(new AppUser("mallory", "Mallory", null, false))).isEqualTo(UserStatus.DISABLED);
	}

}
