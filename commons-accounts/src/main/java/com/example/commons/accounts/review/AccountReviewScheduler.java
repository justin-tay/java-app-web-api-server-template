package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.settings.Settings;
import com.example.commons.accounts.settings.SettingsService;

/**
 * Creates the account review task for the current review window on the first run in it
 * (see docs/adr/0032). It reads the {@code review.*} settings on every run and does
 * nothing when {@code review.enabled} is false.
 *
 * <p>
 * An open task, which is by then overdue, suppresses the next one with a warning, so only
 * one account review is open at a time. It is safe to run on several instances at once:
 * the unique constraint on a task's type and start date lets exactly one create the
 * window's task.
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
		if (!review.enabled()) {
			return;
		}
		ReviewWindow window = ReviewWindow.containing(LocalDate.ofInstant(this.clock.instant(), this.zone),
				review.intervalMonths());
		if (this.tasks.existsByTypeAndStartDate(Task.ACCOUNT_REVIEW, window.start())) {
			return;
		}
		if (this.tasks.existsByTypeAndStatus(Task.ACCOUNT_REVIEW, TaskStatus.OPEN)) {
			LOGGER.warn("The account review for the window starting {} was not created because an earlier one is "
					+ "still open", window.start());
			return;
		}
		try {
			this.service.createTask(window);
			LOGGER.info("Created the account review for the window {} to {}", window.start(), window.due());
		}
		catch (DataIntegrityViolationException ex) {
			// Another instance created the window's task first.
		}
	}

}
