package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppSettingRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReviewItemRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.settings.SettingsService;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Tests {@link AccountReviewScheduler} against the real schema, with the default settings
 * the migration seeds: a three-month window.
 */
@AccountsJpaTest
class AccountReviewSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-05-15T10:00:00Z");

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private ReviewItemRepository items;

	@Autowired
	private AccountAuditEventRepository auditEvents;

	@Autowired
	private AppSettingRepository settings;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private AccountReviewScheduler scheduler;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccountAuditLogger auditLogger = new AccountAuditLogger(this.auditEvents, clock);
		ReviewItems reviewItems = new ReviewItems(this.items, this.tasks, clock);
		AccountLifecycleService lifecycle = new AccountLifecycleService(this.users,
				mock(SessionRevocationService.class), auditLogger, null, reviewItems, clock);
		AccountReviewService service = new AccountReviewService(this.tasks, this.items, this.users, this.auditEvents,
				lifecycle, reviewItems, auditLogger, clock, ZoneOffset.UTC);
		this.scheduler = new AccountReviewScheduler(this.tasks, service,
				new SettingsService(this.settings, auditLogger), clock, ZoneOffset.UTC);
		AppGroup group = this.entityManager.persist(new AppGroup("users"));
		AppUser alice = new AppUser("alice", "Alice", null);
		alice.getGroups().add(group);
		this.entityManager.persist(alice);
	}

	@Test
	void createsTheTaskForTheCurrentWindowOnTheFirstRunAndNotAgain() {
		this.scheduler.run();
		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		Task task = this.tasks.findAll().get(0);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 30));
		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(this.items.findAll()).hasSize(1);
	}

	@Test
	void doesNotCreateAnotherWhileAnEarlierTaskIsStillOpen() {
		this.tasks.save(new Task(Task.ACCOUNT_REVIEW, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), NOW));

		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
	}

	@Test
	void createsTheNextWindowsTaskOnceTheEarlierOneIsComplete() {
		Task earlier = new Task(Task.ACCOUNT_REVIEW, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), NOW);
		earlier.complete(NOW, "rachel");
		this.tasks.save(earlier);

		this.scheduler.run();

		assertThat(this.tasks.findAll()).extracting(Task::getStartDate)
			.containsExactlyInAnyOrder(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));
	}

	@Test
	void doesNothingWhileTheReviewSettingIsOff() {
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = 'false' WHERE name = 'review.enabled'");

		this.scheduler.run();

		assertThat(this.tasks.findAll()).isEmpty();
	}

	@Test
	void followsAChangedWindowLength() {
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = '1' WHERE name = 'review.intervalMonths'");

		this.scheduler.run();

		Task task = this.tasks.findAll().get(0);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 31));
	}

}
