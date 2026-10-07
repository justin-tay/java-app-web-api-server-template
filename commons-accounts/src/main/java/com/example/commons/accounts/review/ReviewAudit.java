package com.example.commons.accounts.review;

import java.util.List;
import java.util.UUID;

import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.Task;
import com.example.commons.audit.AuditAction;
import com.example.commons.audit.AuditEvent;
import com.example.commons.audit.AuditTarget;

/**
 * The account review's audit actions and the details each one keeps in the audit trail
 * (see docs/adr/0040). A decision on an item is about the item's account; the task's own
 * events are administrative. A refused decision is recorded under the decision it
 * attempted.
 */
final class ReviewAudit {

	static final AuditAction CREATE_REVIEW_TASK = AuditAction.iam("create_review_task", "admin", "creation",
			"Review task creation");

	static final AuditAction CONFIRM_REVIEW_ITEM = AuditAction.iam("confirm_review_item", "user", "info",
			"Review item confirmation");

	static final AuditAction EDIT_REVIEW_ITEM_ROLES = AuditAction.iam("edit_review_item_roles", "user", "change",
			"Review item roles edit");

	static final AuditAction REMOVE_REVIEW_ITEM = AuditAction.iam("remove_review_item", "user", "deletion",
			"Review item removal");

	static final AuditAction CONFIRM_REVIEW_POPULATION = AuditAction.iam("confirm_review_population", "admin", "info",
			"Review population confirmation");

	static final AuditAction COMPLETE_REVIEW_TASK = AuditAction.iam("complete_review_task", "admin", "change",
			"Review task completion");

	static final AuditAction EXPORT_REVIEW_REPORT = AuditAction.iam("export_review_report", "admin", "info",
			"Review report export");

	private static final String REVIEW = "REVIEW";

	private ReviewAudit() {
	}

	/**
	 * The details of {@code create_review_task}.
	 */
	record TaskCreated(String type, String startDate, String dueDate, int itemCount) {
	}

	/**
	 * The details of a decision on an item, and of its refusal.
	 */
	record ItemDecided(UUID taskId, List<String> rolesRemoved) {
	}

	/**
	 * The details of {@code confirm_review_population}.
	 */
	record PopulationConfirmed(UUID taskId, String population, int count) {
	}

	/**
	 * The details of {@code complete_review_task}.
	 */
	record TaskCompleted(UUID taskId, String sha256, int sizeBytes) {
	}

	/**
	 * The details of {@code export_review_report}.
	 */
	record ReportExported(UUID taskId, String format, boolean draft) {
	}

	/**
	 * Starts an event about an item, whose account is the related user.
	 */
	static AuditEvent.Builder item(AuditAction action, AccountReviewItem item, UUID taskId) {
		return AuditEvent.of(action, AuditTarget.user(REVIEW, item.getPublicId(), item.getUsername(), null))
			.details(new ItemDecided(taskId, null))
			.log("user.target.name", item.getUsername());
	}

	/**
	 * Starts an event about a task.
	 */
	static AuditEvent.Builder task(AuditAction action, Task task, String name) {
		return AuditEvent.of(action, AuditTarget.of(REVIEW, task.getPublicId(), name));
	}

	/**
	 * Starts the refusal of a decision before the items are known, about the task.
	 */
	static AuditEvent.Builder refusal(AuditAction action, UUID taskId) {
		return AuditEvent.of(action, AuditTarget.of(REVIEW, taskId, null)).details(new ItemDecided(taskId, null));
	}

}
