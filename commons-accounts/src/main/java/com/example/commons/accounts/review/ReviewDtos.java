package com.example.commons.accounts.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {

	private ReviewDtos() {
	}

	/**
	 * The number of items in each outcome.
	 */
	public record Counts(long pending, long confirmed, long confirmedRolesEdited, long removed) {
	}

	/**
	 * How far the active accounts of a task are reviewed: the decided ones out of those
	 * in the active category.
	 */
	public record Progress(long reviewed, long total) {
	}

	/**
	 * Whether a population is confirmed, and by whom.
	 */
	public record PopulationStatus(boolean confirmed, String confirmedBy, Instant confirmedAt, String note,
			Integer count) {
	}

	public record Populations(PopulationStatus suspended, PopulationStatus removed) {
	}

	/**
	 * A task in the dashboard.
	 *
	 * @param overdue whether the task is open and past its due date
	 * @param reportAvailable whether the stored report exists, which it does once the
	 * task is completed
	 */
	public record TaskResponse(UUID id, String type, String status, LocalDate startDate, LocalDate dueDate,
			Instant completedAt, String completedBy, boolean overdue, Counts counts, Progress progress,
			Populations populations, boolean reportAvailable) {
	}

	/**
	 * What a client needs for a badge: the open tasks, when the first is due, and how
	 * many are overdue.
	 */
	public record TaskSummary(long openCount, LocalDate earliestDueDate, long overdueCount) {
	}

	/**
	 * One active account of a review task. A pending row shows the live account; a
	 * decided row shows what was frozen when it was decided.
	 *
	 * @param id the item ID
	 * @param roles the roles the account holds, after the decision for a decided row
	 * @param rolesBefore the roles before the decision, null while pending
	 * @param privilegedPermissions the privileged permissions the account held when the
	 * task was created, null in a non-privileged review
	 * @param remark derived text: no changes, the roles removed, or the removal with its
	 * reason
	 * @param ownAccount whether the row is the caller's own account, which they cannot
	 * act on
	 */
	public record ReviewItemResponse(UUID id, UUID userId, String username, String name, String department,
			List<String> roles, List<String> rolesBefore, List<String> privilegedPermissions, Instant lastLoginAt,
			String outcome, String remark, boolean ownAccount, String decidedBy, Instant decidedAt) {
	}

	/**
	 * One account of the suspended or removed population.
	 *
	 * @param occurredAt when the account was suspended or removed
	 * @param actor who suspended or removed it, {@code system} or a username
	 */
	public record PopulationEntryResponse(UUID userId, String username, String name, String department,
			Instant lastLoginAt, Instant occurredAt, String actor, String reasonCode, String reasonNote) {
	}

	public record DecisionRequest(@NotEmpty @Size(max = 100) List<UUID> itemIds,
			@NotNull @Pattern(regexp = "confirm|remove") String decision,
			@Pattern(regexp = "left_organisation|no_longer_required|policy_violation|other") String reasonCode,
			@Size(max = 200) String note) {
	}

	/**
	 * The full set of roles an account should hold, which can only be some of the roles
	 * it holds already, because a reviewer removes access and does not grant it.
	 */
	public record RolesRequest(@NotEmpty Set<UUID> roleIds) {
	}

	public record PopulationConfirmationRequest(@Size(max = 200) String note) {
	}

}
