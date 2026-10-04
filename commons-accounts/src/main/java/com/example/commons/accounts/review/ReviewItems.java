package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.Instant;

import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewItem;
import com.example.commons.accounts.domain.ReviewItemRepository;
import com.example.commons.accounts.domain.ReviewStatus;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;

/**
 * Keeps review items and their tasks consistent when an account is removed, whoever
 * removes it, and completes a task when its last item has been decided (see
 * docs/adr/0032). It runs inside the caller's transaction.
 */
public class ReviewItems {

	private final ReviewItemRepository items;

	private final TaskRepository tasks;

	private final Clock clock;

	public ReviewItems(ReviewItemRepository items, TaskRepository tasks, Clock clock) {
		this.items = items;
		this.tasks = tasks;
		this.clock = clock;
	}

	/**
	 * Marks every undecided item of the account, in tasks still open, as removed,
	 * freezing the account as it stood, so a task can never be left waiting for an
	 * account that no longer exists. Call it before the account is deleted.
	 * @param account the account about to be removed
	 * @param actor who is removing it, or {@code system}
	 * @param reason why it is removed
	 * @param note the optional note
	 */
	public void accountRemoved(AppUser account, String actor, ReasonCode reason, String note) {
		Instant now = this.clock.instant();
		for (ReviewItem item : this.items.findPendingInOpenTasks(account.getPublicId())) {
			item.decide(ReviewStatus.REMOVED, account, actor, now, reason.value(), note);
			this.items.saveAndFlush(item);
			this.tasks.findById(item.getTaskId()).ifPresent(task -> completeIfFinished(task, actor));
		}
	}

	/**
	 * Completes the task when no item is waiting for a decision, recording who made the
	 * last one.
	 */
	public void completeIfFinished(Task task, String by) {
		if (task.isOpen()
				&& this.items.countByTaskIdAndReviewStatus(task.getId(), ReviewStatus.PENDING_VERIFICATION) == 0) {
			task.complete(this.clock.instant(), by);
			this.tasks.save(task);
		}
	}

}
