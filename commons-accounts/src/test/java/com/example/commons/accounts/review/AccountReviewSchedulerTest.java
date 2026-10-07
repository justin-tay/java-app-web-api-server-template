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
				new SettingsService(this.settings, this.trail), this.clock, java.time.ZoneOffset.UTC);
		user("alice");
		setInterval("review.privilegedIntervalMonths", 3);
	}

	private void setInterval(String name, int months) {
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = ? WHERE name = ?", String.valueOf(months),
				name);
	}

	@Test
	void createsThePrivilegedTaskInItsReviewMonthOnTheFirstRunAndNotAgain() {
		this.scheduler.run();
		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		Task task = this.tasks.findAll().get(0);
		assertThat(task.getType()).isEqualTo(Task.PRIVILEGED_ACCOUNT_REVIEW);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(this.items.findAll()).hasSize(1);
	}

	@Test
	void createsTheNonPrivilegedTaskOnItsOwnIntervalAndCoversTheOtherAccounts() {
		plainUser("bob");
		this.clock.set(Instant.parse("2027-01-15T10:00:00Z"));

		this.scheduler.run();
		flushAndClear();

		assertThat(this.tasks.findAll()).extracting(Task::getType)
			.containsExactlyInAnyOrder(Task.PRIVILEGED_ACCOUNT_REVIEW, Task.NON_PRIVILEGED_ACCOUNT_REVIEW);
		Task privileged = this.tasks.findAll()
			.stream()
			.filter(task -> task.getType().equals(Task.PRIVILEGED_ACCOUNT_REVIEW))
			.findFirst()
			.orElseThrow();
		Task nonPrivileged = this.tasks.findAll()
			.stream()
			.filter(task -> task.getType().equals(Task.NON_PRIVILEGED_ACCOUNT_REVIEW))
			.findFirst()
			.orElseThrow();
		assertThat(this.items.findAll().stream().filter(item -> item.getTaskId().equals(privileged.getId())))
			.extracting(item -> item.getUsername())
			.containsExactly("alice");
		assertThat(this.items.findAll().stream().filter(item -> item.getTaskId().equals(nonPrivileged.getId())))
			.extracting(item -> item.getUsername())
			.containsExactly("bob");
	}

	@Test
	void theTwoReviewsFollowTheirOwnIntervals() {
		setInterval("review.privilegedIntervalMonths", 1);
		setInterval("review.nonPrivilegedIntervalMonths", 6);
		this.clock.set(Instant.parse("2026-11-15T10:00:00Z"));

		this.scheduler.run();

		assertThat(this.tasks.findAll()).extracting(Task::getType).containsExactly(Task.PRIVILEGED_ACCOUNT_REVIEW);
		this.clock.set(Instant.parse("2026-07-15T10:00:00Z"));

		this.scheduler.run();

		assertThat(this.tasks.findAll()).extracting(Task::getType)
			.containsExactlyInAnyOrder(Task.PRIVILEGED_ACCOUNT_REVIEW, Task.PRIVILEGED_ACCOUNT_REVIEW,
					Task.NON_PRIVILEGED_ACCOUNT_REVIEW);
	}

	@Test
	void createsNothingInAMonthThatIsNotAReviewMonth() {
		this.clock.set(Instant.parse("2026-11-15T10:00:00Z"));

		this.scheduler.run();

		assertThat(this.tasks.findAll()).isEmpty();
	}

	@Test
	void createsTheNextTaskEvenWhileAnEarlierOneIsStillOpen() {
		this.tasks
			.save(new Task(Task.PRIVILEGED_ACCOUNT_REVIEW, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), NOW));

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
		setInterval("review.privilegedIntervalMonths", 1);
		this.clock.set(Instant.parse("2026-11-15T10:00:00Z"));

		this.scheduler.run();

		Task task = this.tasks.findAll().get(0);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
	}

	@Test
	void anExistingTaskKeepsItsDatesWhenTheIntervalChanges() {
		this.scheduler.run();
		setInterval("review.privilegedIntervalMonths", 12);

		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		assertThat(this.tasks.findAll().get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	@Test
	void stillCompletesAFinishedTaskWhileTheReviewSettingIsOff() {
		this.scheduler.run();
		Task task = this.tasks.findAll().get(0);
		authenticateAsReviewer("ravi");
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();
		this.jdbcTemplate.update("UPDATE app_setting SET setting_value = 'false' WHERE name = 'review.enabled'");

		this.scheduler.run();

		assertThat(this.tasks.findAll().get(0).getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(Set.copyOf(this.tasks.findAll())).hasSize(1);
	}

}
