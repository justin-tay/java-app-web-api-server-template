package com.example.commons.accounts.review;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AccountReviewAttestationRepository;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AccountReviewPopulationEntryRepository;
import com.example.commons.accounts.domain.AccountReviewReportRepository;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Builds the account review service against the real schema for the review tests, with a
 * clock a test can move.
 */
abstract class AccountReviewTestSupport {

	static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

	static final ReviewPeriod OCTOBER = new ReviewPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	@Autowired
	protected TestEntityManager entityManager;

	@Autowired
	protected AppUserRepository users;

	@Autowired
	protected AppGroupRepository groups;

	@Autowired
	protected TaskRepository tasks;

	@Autowired
	protected AccountReviewItemRepository items;

	@Autowired
	protected AccountReviewAttestationRepository attestations;

	@Autowired
	protected AccountReviewPopulationEntryRepository entries;

	@Autowired
	protected AccountReviewReportRepository storedReports;

	@Autowired
	protected AccountAuditEventRepository auditEvents;

	protected final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);

	protected final SettableClock clock = new SettableClock(NOW);

	protected AccountReviewService service;

	protected AccountLifecycleService lifecycle;

	protected AppGroup group;

	@BeforeEach
	void setUpService() {
		AccountAuditLogger auditLogger = new AccountAuditLogger(this.auditEvents, this.clock);
		ReviewItems reviewItems = new ReviewItems(this.items, this.clock);
		this.lifecycle = new AccountLifecycleService(this.users, this.sessionRevocationService, auditLogger, null,
				reviewItems, this.clock);
		AccountReviewReports reports = new AccountReviewReports(this.storedReports, new DefaultReviewReportRenderer(),
				auditLogger, this.clock);
		this.service = new AccountReviewService(this.tasks, this.items, this.attestations, this.entries, this.users,
				this.groups, this.auditEvents, this.lifecycle, this.sessionRevocationService, auditLogger, reports,
				this.clock, ZoneOffset.UTC);
		this.group = this.entityManager.persist(new AppGroup("users"));
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	protected AppUser user(String username) {
		return user(username, null);
	}

	protected AppUser user(String username, String department) {
		AppUser user = new AppUser(username, username, null, department);
		user.getGroups().add(this.group);
		return this.entityManager.persist(user);
	}

	protected void flushAndClear() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	protected Task reload(Task task) {
		return this.tasks.findByPublicId(task.getPublicId()).orElseThrow();
	}

	protected AccountReviewItem itemOf(Task task, String username) {
		return this.items.findAll()
			.stream()
			.filter(item -> item.getTaskId().equals(task.getId()) && item.getUsername().equals(username))
			.findFirst()
			.orElseThrow();
	}

	protected static void authenticateAs(String username, String... roles) {
		String[] authorities = new String[roles.length];
		for (int i = 0; i < roles.length; i++) {
			authorities[i] = "ROLE_" + roles[i];
		}
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(username, null, authorities));
	}

	/**
	 * A clock whose instant a test can set.
	 */
	static final class SettableClock extends Clock {

		private Instant instant;

		SettableClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

	}

}
