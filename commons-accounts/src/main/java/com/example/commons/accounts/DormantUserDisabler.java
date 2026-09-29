package com.example.commons.accounts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.admin.AdministrationAuditLogger;
import com.example.commons.accounts.admin.AdministrationAuditLogger.UserState;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Disables enabled users with no sign-in, creation, or administrative change for longer
 * than a threshold (see docs/adr/0028). Each disabled user's sessions are ended and the
 * change is audited as an {@code update_user} event with reason {@code dormant_account}.
 * Enabling a user again is an administrative change, so it gives them a full threshold to
 * sign in before they are disabled again.
 *
 * <p>
 * It is safe to run on several instances at once, since disabling an already disabled
 * user finds nothing to do.
 */
public class DormantUserDisabler {

	private static final Logger LOGGER = LoggerFactory.getLogger(DormantUserDisabler.class);

	private final AppUserRepository users;

	private final SessionRevocationService sessionRevocationService;

	private final AdministrationAuditLogger auditLogger;

	private final Duration threshold;

	private final Clock clock;

	public DormantUserDisabler(AppUserRepository users, SessionRevocationService sessionRevocationService,
			AdministrationAuditLogger auditLogger, Duration threshold, Clock clock) {
		this.users = users;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogger = auditLogger;
		this.threshold = threshold;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "${commons.accounts.dormancy.check-interval:PT1H}",
			fixedDelayString = "${commons.accounts.dormancy.check-interval:PT1H}")
	@Transactional
	public int disableDormantUsers() {
		Instant cutoff = this.clock.instant().minus(this.threshold);
		List<AppUser> dormant = this.users.findEnabledInactiveSince(cutoff);
		for (AppUser user : dormant) {
			UserState before = UserState.of(user);
			user.update(user.getName(), user.getEmail(), false);
			this.sessionRevocationService.revoke(user.getUsername(), "dormant_account");
			this.auditLogger.userUpdated(before, UserState.of(user), "dormant_account");
		}
		if (!dormant.isEmpty()) {
			LOGGER.info("Disabled {} dormant users", dormant.size());
		}
		return dormant.size();
	}

}
