package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AppGroup;
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
			item.remove(account, groupNames(account), actor, now, removalEvent == null ? null : removalEvent.getId());
			this.items.saveAndFlush(item);
		}
	}

	/**
	 * Returns the names of an account's groups, sorted.
	 */
	static List<String> groupNames(AppUser account) {
		return account.getGroups().stream().map(AppGroup::getName).sorted(Comparator.naturalOrder()).toList();
	}

}
