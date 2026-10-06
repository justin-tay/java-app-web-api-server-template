package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AccountReviewOutcome;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;

/**
 * Assembles the report of an account review task from its items and populations, which
 * are frozen for what has been decided and confirmed and live for the rest. It runs
 * inside the caller's transaction.
 */
public class ReviewReportModels {

	private final AccountReviewItemRepository items;

	private final AccountAuditEventRepository auditEvents;

	private final ReviewPopulations populations;

	private final Clock clock;

	private final ZoneId zone;

	public ReviewReportModels(AccountReviewItemRepository items, AccountAuditEventRepository auditEvents,
			ReviewPopulations populations, Clock clock, ZoneId zone) {
		this.items = items;
		this.auditEvents = auditEvents;
		this.populations = populations;
		this.clock = clock;
		this.zone = zone;
	}

	/**
	 * Returns the report of a task.
	 * @param task the task
	 * @param draft whether the task is still open, so the report is marked as a draft
	 * @param generatedBy who generates it
	 * @return the model the renderers lay out
	 */
	public ReviewReportModel build(Task task, boolean draft, String generatedBy) {
		List<AccountReviewItem> all = this.items.findAllWithAccount(task.getId());
		Map<Long, AccountAuditEvent> removals = this.auditEvents
			.findAllById(all.stream().map(AccountReviewItem::getRemovalAuditEventId).filter(Objects::nonNull).toList())
			.stream()
			.collect(Collectors.toMap(AccountAuditEvent::getId, event -> event));
		List<ReviewReportModel.ItemRow> rows = new ArrayList<>();
		Map<String, Tally> byDepartment = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Tally total = new Tally();
		List<AccountReviewItem> ordered = all.stream()
			.sorted(Comparator.comparing(item -> ReviewItemView.of(item, null).name(), String.CASE_INSENSITIVE_ORDER))
			.toList();
		for (AccountReviewItem item : ordered) {
			AccountAuditEvent removal = item.getRemovalAuditEventId() == null ? null
					: removals.get(item.getRemovalAuditEventId());
			ReviewItemView view = ReviewItemView.of(item, removal == null ? null : removal.getReasonCode());
			rows.add(new ReviewReportModel.ItemRow(rows.size() + 1, view.name(), item.getUsername(), view.department(),
					String.join(", ", view.roles()), outcomeLabel(item.getOutcome()), view.remark(),
					item.getDecidedAt()));
			byDepartment.computeIfAbsent(view.department() == null ? "(none)" : view.department(), key -> new Tally())
				.add(item.getOutcome());
			total.add(item.getOutcome());
		}
		List<ReviewReportModel.DepartmentRow> departments = byDepartment.entrySet()
			.stream()
			.map(entry -> entry.getValue().departmentRow(entry.getKey()))
			.toList();
		return new ReviewReportModel(draft, task.isPrivilegedReview(), this.zone, task.getStartDate(),
				task.getDueDate(), task.getDueDate(), task.getCompletedAt(), task.getCompletedBy(),
				this.clock.instant(), generatedBy, total.summary(), departments, rows,
				this.populations.section(task, ReviewPopulation.SUSPENDED),
				this.populations.section(task, ReviewPopulation.REMOVED));
	}

	private static String outcomeLabel(AccountReviewOutcome outcome) {
		return switch (outcome) {
			case PENDING -> "Pending";
			case CONFIRMED -> "Confirmed";
			case CONFIRMED_ROLES_EDITED -> "Confirmed (Roles Edited)";
			case REMOVED -> "Removed";
		};
	}

	/**
	 * The number of items in each outcome, and in all.
	 */
	private static final class Tally {

		private final Map<AccountReviewOutcome, Long> counts = new EnumMap<>(AccountReviewOutcome.class);

		private long total;

		void add(AccountReviewOutcome outcome) {
			this.counts.merge(outcome, 1L, Long::sum);
			this.total++;
		}

		private long of(AccountReviewOutcome outcome) {
			return this.counts.getOrDefault(outcome, 0L);
		}

		ReviewReportModel.Summary summary() {
			return new ReviewReportModel.Summary(of(AccountReviewOutcome.CONFIRMED),
					of(AccountReviewOutcome.CONFIRMED_ROLES_EDITED), of(AccountReviewOutcome.REMOVED),
					of(AccountReviewOutcome.PENDING), this.total);
		}

		ReviewReportModel.DepartmentRow departmentRow(String department) {
			return new ReviewReportModel.DepartmentRow(department, of(AccountReviewOutcome.CONFIRMED),
					of(AccountReviewOutcome.CONFIRMED_ROLES_EDITED), of(AccountReviewOutcome.REMOVED),
					of(AccountReviewOutcome.PENDING), this.total);
		}

	}

}
