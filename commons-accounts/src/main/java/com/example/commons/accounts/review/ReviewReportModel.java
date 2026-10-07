package com.example.commons.accounts.review;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;

/**
 * Everything an account review report shows, assembled from the frozen records of the
 * task (and, for a draft, the live data), so the renderers only lay it out.
 *
 * @param draft whether the task is still open, in which case the report is marked as a
 * draft and is not evidence
 * @param privileged whether the task is the privileged account review, which the title of
 * the report names
 * @param zone the time zone the times are shown in
 */
public record ReviewReportModel(boolean draft, boolean privileged, ZoneId zone, LocalDate periodStart,
		LocalDate periodEnd, LocalDate dueDate, Instant completedAt, String completedBy, Instant generatedAt,
		String generatedBy, ItemSection active, ItemSection suspended, PopulationSection removed) {

	/**
	 * The accounts of one category with their outcomes, in all and by department.
	 */
	public record ItemSection(Summary summary, List<DepartmentRow> departments, List<ItemRow> items) {
	}

	/**
	 * The number of accounts in each outcome.
	 */
	public record Summary(long confirmed, long confirmedRolesEdited, long removed, long pending, long total) {
	}

	/**
	 * The outcomes of one department's accounts.
	 */
	public record DepartmentRow(String department, long confirmed, long confirmedRolesEdited, long removed,
			long pending, long total) {
	}

	/**
	 * One account of the review, with the roles it holds after the review. The suspension
	 * fields are null for an active account, and the remark of a suspended account
	 * includes its suspension note.
	 */
	public record ItemRow(int number, String name, String username, String department, Instant createdAt,
			Instant lastLoginAt, String roles, Instant suspendedAt, String suspendedBy, String suspensionReason,
			String outcome, String remark, Instant decidedAt) {
	}

	/**
	 * One population with its confirmation, if it has been confirmed.
	 */
	public record PopulationSection(boolean confirmed, String confirmedBy, Instant confirmedAt, String note,
			List<PopulationEntryResponse> entries) {
	}

}
