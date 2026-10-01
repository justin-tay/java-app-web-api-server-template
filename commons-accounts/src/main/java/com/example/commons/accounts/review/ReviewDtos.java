package com.example.commons.accounts.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.example.commons.accounts.validation.ResourceId;

public final class ReviewDtos {

	private ReviewDtos() {
	}

	/**
	 * A task in the dashboard.
	 *
	 * @param overdue whether the task is open and past its due date
	 * @param counts the number of items in each review status, keyed by the status value
	 */
	public record TaskResponse(String id, String type, String status, LocalDate startDate, LocalDate dueDate,
			Instant completedAt, String completedBy, boolean overdue, Map<String, Long> counts) {
	}

	/**
	 * What a client needs for a badge: the open tasks, when the first is due, and how
	 * many are overdue.
	 */
	public record TaskSummary(long openCount, LocalDate earliestDueDate, long overdueCount) {
	}

	/**
	 * One row of a review task. Which fields are filled depends on the category: active
	 * rows have {@code lastLoginAt}, suspended rows {@code suspendedAt}, and removed rows
	 * {@code removedAt} and {@code removedBy}; suspended and removed rows have the
	 * reason.
	 *
	 * @param id the item ID, or for a removed row the ID of the removal's audit event
	 * @param ownAccount whether the row is the caller's own account, which they cannot
	 * act on
	 */
	public record ReviewItemResponse(String id, String userId, String username, String name, String category,
			String reviewStatus, boolean ownAccount, Instant lastLoginAt, Instant suspendedAt, Instant removedAt,
			String removedBy, String reasonCode, String reasonNote, String decidedBy, Instant decidedAt) {
	}

	public record DecisionRequest(@NotEmpty @Size(max = 100) List<@ResourceId String> itemIds,
			@NotNull @Pattern(regexp = "verify|remove") String decision,
			@Pattern(regexp = "left_organisation|no_longer_required|policy_violation|other") String reasonCode,
			@Size(max = 200) String note) {
	}

}
