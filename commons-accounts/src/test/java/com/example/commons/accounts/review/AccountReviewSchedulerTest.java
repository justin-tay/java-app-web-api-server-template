package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.domain.AppSettingRepository;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.AccountReviewService.Decision;
import com.example.commons.accounts.settings.SettingsService;

/**
 * Tests {@link AccountReviewScheduler} against the real schema, with the default settings
 * the migration seeds: a review every three months, in January, April, July and October.
 */
@AccountsJpaTest
class AccountReviewSchedulerTest extends AccountReviewTestSupport {

	@Autowired
	private AppSettingRepository settings;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private AccountReviewScheduler scheduler;

	@BeforeEach
	void setUpScheduler() {
		this.scheduler = new AccountReviewScheduler(this.tasks, this.service,
				new SettingsService(this.settings, new AccountAuditLogger(this.auditEvents, this.clock)), this.clock,
				java.time.ZoneOffset.UTC);
		user("alice");
	}

	@Test
	void createsTheTaskInAReviewMonthOnTheFirstRunAndNotAgain() {
		this.scheduler.run();
		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		Task task = this.tasks.findAll().get(0);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(this.items.findAll()).hasSize(1);
	}

	@Test
	void createsNothingInAMonthThatIsNotAReviewMonth() {
		this.clock.set(Instant.parse("2026-11-15T10:00:00Z"));

		this.scheduler.run();

		assertThat(this.tasks.findAll()).isEmpty();
	}

	@Test
	void createsTheNextTaskEvenWhileAnEarlierOneIsStillOpen() {
		this.tasks.save(new Task(Task.ACCOUNT_REVIEW, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), NOW));

		this.scheduler.run();

		assertThat(this.tasks.findAll()).extracting(Task::getStartDate)
			.containsExactlyInAnyOrder(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1));
		assertThat(this.tasks.findAll()).extracting(Task::getStatus).containsOnly(TaskStatus.OPEN);
	}

	@Test
	void doesNotCreateATaskWhileTheReviewSettingIsOff() {
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = 'false' WHERE name = 'review.enabled'");

		this.scheduler.run();

		assertThat(this.tasks.findAll()).isEmpty();
	}

	@Test
	void followsAChangedInterval() {
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = '1' WHERE name = 'review.intervalMonths'");
		this.clock.set(Instant.parse("2026-11-15T10:00:00Z"));

		this.scheduler.run();

		Task task = this.tasks.findAll().get(0);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
	}

	@Test
	void anExistingTaskKeepsItsDatesWhenTheIntervalChanges() {
		this.scheduler.run();
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = '12' WHERE name = 'review.intervalMonths'");

		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		assertThat(this.tasks.findAll().get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	@Test
	void stillCompletesAFinishedTaskWhileTheReviewSettingIsOff() {
		this.scheduler.run();
		Task task = this.tasks.findAll().get(0);
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = 'false' WHERE name = 'review.enabled'");

		this.scheduler.run();

		assertThat(this.tasks.findAll().get(0).getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(Set.copyOf(this.tasks.findAll())).hasSize(1);
	}

}
