package com.example.commons.accounts.review;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.audit.AccountAudit;
import com.example.commons.accounts.domain.AccountReviewAttestationRepository;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AccountReviewPopulationEntryRepository;
import com.example.commons.accounts.domain.AccountReviewReportRepository;
import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppPermissionRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.audit.AuditQuery;
import com.example.commons.audit.AuditRecord;
import com.example.commons.audit.AuditTrail;
import com.example.commons.audit.AuditTrailEventRepository;
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
	protected AppRoleRepository roles;

	@Autowired
	protected AppPermissionRepository permissions;

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
	private AuditTrailEventRepository auditEvents;

	@Autowired
	private PlatformTransactionManager transactionManager;

	protected final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);

	protected final SettableClock clock = new SettableClock(NOW);

	protected AccountReviewService service;

	/**
	 * The audit trail, on the clock a test can move.
	 */
	protected AuditTrail trail;

	protected ReviewPopulations populations;

	protected ReviewReportModels reportModels;

	protected AccountLifecycleService lifecycle;

	/**
	 * The role the test users hold. It has a privileged permission, so they are
	 * privileged accounts and a privileged review covers them.
	 */
	protected AppRole role;

	/**
	 * A role with no privileged permission, for the accounts a non-privileged review
	 * covers.
	 */
	protected AppRole plainRole;

	@BeforeEach
	void setUpService() {
		this.trail = new AuditTrail(this.auditEvents, this.transactionManager, this.clock);
		AccountAudit audit = new AccountAudit(this.trail);
		ReviewItems reviewItems = new ReviewItems(this.items, this.clock);
		this.lifecycle = new AccountLifecycleService(this.users, this.sessionRevocationService, audit, null,
				reviewItems, this.clock);
		AccountReviewReports reports = new AccountReviewReports(this.storedReports, new DefaultReviewReportRenderer(),
				this.trail, this.clock);
		this.populations = new ReviewPopulations(this.tasks, this.attestations, this.entries, audit, this.clock,
				ZoneOffset.UTC);
		this.reportModels = new ReviewReportModels(this.items, audit, this.populations, this.clock, ZoneOffset.UTC);
		this.service = new AccountReviewService(this.tasks, this.items, this.users, this.roles, this.lifecycle,
				this.sessionRevocationService, audit, this.trail, reports, this.populations, this.reportModels,
				this.clock, ZoneOffset.UTC);
		AppRole privileged = new AppRole("users");
		privileged.getPermissions().add(permission(Permissions.USER_CREATE));
		this.role = this.entityManager.persist(privileged);
		AppRole plain = new AppRole("readers");
		plain.getPermissions().add(permission(Permissions.APPLICATION_ACCESS));
		this.plainRole = this.entityManager.persist(plain);
	}

	/**
	 * Returns every event in the audit trail, refusals included, oldest first.
	 */
	protected List<AuditRecord> auditEvents() {
		return this.trail.find(AuditQuery.where().anyOutcome());
	}

	protected AppPermission permission(String name) {
		return this.permissions.findAll()
			.stream()
			.filter(permission -> permission.getName().equals(name))
			.findFirst()
			.orElseThrow();
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
		user.getRoles().add(this.role);
		return this.entityManager.persist(user);
	}

	/**
	 * Creates an account that is not privileged.
	 */
	protected AppUser plainUser(String username) {
		AppUser user = new AppUser(username, username, null, null);
		user.getRoles().add(this.plainRole);
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

	/**
	 * Signs in as the user with the given permissions as authorities.
	 */
	protected static void authenticateAs(String username, String... permissions) {
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(username, null, permissions));
	}

	/**
	 * Signs in as a user with what the Account Reviewers role grants: the review
	 * permissions and removing roles and accounts, but nothing that adds.
	 */
	protected static void authenticateAsReviewer(String username) {
		authenticateAs(username, Permissions.REVIEW_READ, Permissions.REVIEW_DECIDE,
				Permissions.REVIEW_CONFIRM_POPULATION, Permissions.REVIEW_DOWNLOAD_REPORT, Permissions.USER_READ,
				Permissions.USER_REMOVE_ROLE, Permissions.USER_REMOVE);
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
