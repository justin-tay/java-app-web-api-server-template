package com.example.commons.accounts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.settings.Settings;
import com.example.commons.accounts.settings.SettingsService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Suspends accounts that have not been in use for {@code inactivity.suspendAfterDays} and
 * removes those not in use for {@code inactivity.removeAfterDays}, with reason
 * {@code inactive_account} (see docs/adr/0031). An account was last in use at the later
 * of its last sign-in and the time it was created or last unsuspended.
 *
 * <p>
 * It reads the settings on every run, so a change takes effect without a restart, and
 * does nothing when {@code inactivity.enabled} is false. Each account is changed in its
 * own transaction, so one failure does not block the rest. It is safe to run on several
 * instances at once: an account another instance has already handled is skipped.
 */
public class InactiveUserSuspender {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactiveUserSuspender.class);

	private final AppUserRepository users;

	private final AccountLifecycleService lifecycle;

	private final SettingsService settings;

	private final Clock clock;

	public InactiveUserSuspender(AppUserRepository users, AccountLifecycleService lifecycle, SettingsService settings,
			Clock clock) {
		this.users = users;
		this.lifecycle = lifecycle;
		this.settings = settings;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "${commons.accounts.inactivity.check-interval:PT1H}",
			fixedDelayString = "${commons.accounts.inactivity.check-interval:PT1H}")
	public void run() {
		Settings.Inactivity policy = this.settings.get().inactivity();
		if (!policy.enabled()) {
			return;
		}
		Instant now = this.clock.instant();
		int removed = removeInactive(now.minus(Duration.ofDays(policy.removeAfterDays())));
		int suspended = suspendInactive(now.minus(Duration.ofDays(policy.suspendAfterDays())));
		if (removed > 0 || suspended > 0) {
			LOGGER.info("Suspended {} and removed {} inactive accounts", suspended, removed);
		}
	}

	private int removeInactive(Instant cutoff) {
		int count = 0;
		List<UUID> ids = this.users.findPublicIdsInactiveSince(cutoff);
		for (UUID id : ids) {
			try {
				this.lifecycle.remove(id, ReasonCode.INACTIVE_ACCOUNT, null);
				count++;
			}
			catch (ResourceNotFoundException ex) {
				// Another instance removed it first.
			}
		}
		return count;
	}

	private int suspendInactive(Instant cutoff) {
		int count = 0;
		List<UUID> ids = this.users.findActivePublicIdsInactiveSince(cutoff);
		for (UUID id : ids) {
			try {
				this.lifecycle.suspend(id, ReasonCode.INACTIVE_ACCOUNT, null);
				count++;
			}
			catch (ResourceNotFoundException | ConflictException ex) {
				// Another instance handled it first.
			}
		}
		return count;
	}

}
