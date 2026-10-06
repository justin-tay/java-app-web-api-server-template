package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;

/**
 * Keeps review items consistent when an account is removed, whoever removes it (see
 * docs/adr/0037). It runs inside the caller's transaction.
 */
public class ReviewItems {

	private final AccountReviewItemRepository items;

	private final Clock clock;

	public ReviewItems(AccountReviewItemRepository items, Clock clock) {
		this.items = items;
		this.clock = clock;
	}

	/**
	 * Marks every undecided item of the account, in tasks still open, as removed,
	 * freezing the account as it stands and pointing at the removal's audit event. A
	 * reviewer's own removal reaches the item here as well. Call it before the account is
	 * deleted.
	 * @param account the account about to be removed
	 * @param actor who is removing it, or {@code system}
	 * @param removalEvent the removal's audit event, or null when no audit trail is kept
	 */
	public void accountRemoved(AppUser account, String actor, AccountAuditEvent removalEvent) {
		Instant now = this.clock.instant();
		for (AccountReviewItem item : this.items.findPendingInOpenTasks(account.getPublicId())) {
			item.remove(account, roleNames(account), actor, now, removalEvent == null ? null : removalEvent.getId());
			this.items.saveAndFlush(item);
		}
	}

	/**
	 * Returns the names of an account's roles, sorted.
	 */
	static List<String> roleNames(AppUser account) {
		return account.getRoles().stream().map(AppRole::getName).sorted(Comparator.naturalOrder()).toList();
	}

	/**
	 * Returns the privileged permissions an account holds, sorted.
	 */
	static List<String> privilegedPermissions(AppUser account) {
		return account.permissions()
			.stream()
			.filter(AppPermission::isPrivileged)
			.map(AppPermission::getName)
			.sorted(Comparator.naturalOrder())
			.toList();
	}

}
