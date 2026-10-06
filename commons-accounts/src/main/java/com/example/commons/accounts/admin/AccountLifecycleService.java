package com.example.commons.accounts.admin;

import java.util.UUID;

import java.time.Clock;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.admin.AccountAuditLogger.UserState;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.Auditor;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.review.ReviewItems;
import com.example.commons.security.authentication.passkey.PasskeyManager;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Suspends, unsuspends, and removes accounts. It is the one place that does, whoever
 * asks: an administrator, an account reviewer, or the inactivity job, so every path
 * applies the same rules and writes the same audit event (see docs/adr/0031).
 *
 * <p>
 * Suspending ends the account's sessions. Removing deletes the account, its role
 * memberships and its passkeys in one transaction, ends its sessions, and leaves only the
 * audit trail and any review item, which hold no foreign key to the account. An
 * authenticated actor cannot change their own account (see docs/adr/0038); a change the
 * application makes itself, with no authenticated user, is trusted.
 */
@Transactional
public class AccountLifecycleService {

	private final AppUserRepository users;

	private final SessionRevocationService sessionRevocationService;

	private final AccountAuditLogger auditLogger;

	private final PasskeyManager passkeyManager;

	private final ReviewItems reviewItems;

	private final Clock clock;

	/**
	 * Creates the service.
	 * @param passkeyManager the passkey manager, or null when passkeys are not enabled,
	 * used to delete a removed account's passkeys
	 * @param reviewItems marks the account's open review items removed, or null when
	 * there is no account review
	 */
	public AccountLifecycleService(AppUserRepository users, SessionRevocationService sessionRevocationService,
			AccountAuditLogger auditLogger, PasskeyManager passkeyManager, ReviewItems reviewItems, Clock clock) {
		this.users = users;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogger = auditLogger;
		this.passkeyManager = passkeyManager;
		this.reviewItems = reviewItems;
		this.clock = clock;
	}

	public AppUser suspend(UUID id, ReasonCode reason, String note) {
		return suspend(user(id), reason, note);
	}

	public AppUser suspend(AppUser user, ReasonCode reason, String note) {
		UserState before = UserState.of(user);
		rejectSelfModification(user, before, false);
		if (user.isSuspended()) {
			this.auditLogger.userUpdateRejected(before, "already_suspended");
			throw new ConflictException("Account is already suspended.");
		}
		user.suspend(this.clock.instant(), reason, note);
		this.sessionRevocationService.revoke(user.getUsername(), "account_suspended");
		this.auditLogger.userSuspended(before, UserState.of(user), reason, note);
		return user;
	}

	public AppUser unsuspend(UUID id) {
		AppUser user = user(id);
		UserState before = UserState.of(user);
		rejectSelfModification(user, before, false);
		if (!user.isSuspended()) {
			this.auditLogger.userUpdateRejected(before, "not_suspended");
			throw new ConflictException("Account is not suspended.");
		}
		user.unsuspend(this.clock.instant());
		this.auditLogger.userUnsuspended(before, UserState.of(user));
		return user;
	}

	public void remove(UUID id, ReasonCode reason, String note) {
		remove(user(id), reason, note);
	}

	public void remove(AppUser user, ReasonCode reason, String note) {
		UserState before = UserState.of(user);
		rejectSelfModification(user, before, true);
		this.sessionRevocationService.revoke(user.getUsername(), "account_deleted");
		if (this.passkeyManager != null) {
			this.passkeyManager.removeAll(user.getPublicId().toString());
		}
		AccountAuditEvent event = this.auditLogger.userDeleted(before, reason, note);
		if (this.reviewItems != null) {
			this.reviewItems.accountRemoved(user, Auditor.current(), event);
		}
		this.users.delete(user);
	}

	public AppUser user(UUID id) {
		return this.users.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("User"));
	}

	private void rejectSelfModification(AppUser user, UserState state, boolean deletion) {
		boolean self = Actor.current().map(actor -> actor.name().equals(user.getUsername())).orElse(false);
		if (self) {
			if (deletion) {
				this.auditLogger.userDeletionRejected(state, "self_modification");
			}
			else {
				this.auditLogger.userUpdateRejected(state, "self_modification");
			}
			throw new AccessDeniedException("Users cannot change their own account.");
		}
	}

}
