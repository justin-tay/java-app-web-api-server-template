package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.settings.Settings;
import com.example.commons.accounts.settings.SettingsService;

/**
 * Creates the account review task on the first run in a review month, and completes tasks
 * that a change outside the review has finished (see docs/adr/0037). It reads the
 * {@code review.*} settings on every run. When {@code review.enabled} is false it creates
 * nothing, but tasks that already exist are still completed when they are finished.
 *
 * <p>
 * A task that is still open does not stop the next one from being created: the two review
 * different data, and the unfinished one stays open and overdue. It is safe to run on
 * several instances at once: the unique constraint on a task's type and start date lets
 * exactly one create the month's task.
 */
public class AccountReviewScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccountReviewScheduler.class);

	private final TaskRepository tasks;

	private final AccountReviewService service;

	private final SettingsService settings;

	private final Clock clock;

	private final ZoneId zone;

	public AccountReviewScheduler(TaskRepository tasks, AccountReviewService service, SettingsService settings,
			Clock clock, ZoneId zone) {
		this.tasks = tasks;
		this.service = service;
		this.settings = settings;
		this.clock = clock;
		this.zone = zone;
	}

	@Scheduled(initialDelayString = "${commons.accounts.review.check-interval:PT1H}",
			fixedDelayString = "${commons.accounts.review.check-interval:PT1H}")
	public void run() {
		Settings.Review review = this.settings.get().review();
		if (review.enabled()) {
			createDueTask(review.intervalMonths());
		}
		completeFinishedTasks();
	}

	private void createDueTask(int intervalMonths) {
		ReviewPeriod.containing(LocalDate.ofInstant(this.clock.instant(), this.zone), intervalMonths)
			.filter(period -> !this.tasks.existsByTypeAndStartDate(Task.ACCOUNT_REVIEW, period.start()))
			.ifPresent(period -> {
				try {
					this.service.createTask(period);
					LOGGER.info("Created the account review for {} to {}", period.start(), period.due());
				}
				catch (DataIntegrityViolationException ex) {
					// Another instance created the month's task first.
				}
			});
	}

	private void completeFinishedTasks() {
		for (UUID taskId : this.service.openTaskIds()) {
			try {
				this.service.completeIfFinished(taskId);
			}
			catch (RuntimeException ex) {
				LOGGER.warn("The account review {} could not be completed and will be retried", taskId, ex);
			}
		}
	}

}
