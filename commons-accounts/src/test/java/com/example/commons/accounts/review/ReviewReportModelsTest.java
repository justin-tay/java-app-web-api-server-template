package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.domain.AccountReviewCategory;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.AccountReviewService.Decision;
import com.example.commons.accounts.review.AccountReviewService.ItemQuery;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;

/**
 * Tests the report of a review task: the tally by outcome and department, and what each
 * row says about the decision.
 */
@AccountsJpaTest
class ReviewReportModelsTest extends AccountReviewTestSupport {

	@Test
	void theReportCountsEachOutcomeInTotalAndByDepartment() {
		user("rachel");
		user("alice", "Finance");
		user("bob", "Finance");
		user("carol", "HR");
		Task task = decidedTask();

		ReviewReportModel model = this.reportModels.build(task, true, "rachel");

		assertThat(model.active().summary()).isEqualTo(new ReviewReportModel.Summary(1, 0, 1, 2, 4));
		assertThat(model.active().departments()).containsExactly(
				new ReviewReportModel.DepartmentRow("(none)", 0, 0, 0, 1, 1),
				new ReviewReportModel.DepartmentRow("Finance", 1, 0, 1, 0, 2),
				new ReviewReportModel.DepartmentRow("HR", 0, 0, 0, 1, 1));
	}

	@Test
	void aRemovedAccountShowsItsReasonInTheReportButNotInTheList() {
		user("rachel");
		user("alice", "Finance");
		user("bob", "Finance");
		Task task = decidedTask();

		ReviewReportModel model = this.reportModels.build(task, true, "rachel");

		assertThat(model.active().items()).extracting(ReviewReportModel.ItemRow::username)
			.containsExactly("alice", "bob", "rachel");
		assertThat(model.active().items()).extracting(ReviewReportModel.ItemRow::remark)
			.containsExactly("No changes", "Account removed (no_longer_required)", null);
		List<ReviewItemResponse> listed = this.service
			.items(task.getPublicId(), new ItemQuery(AccountReviewCategory.ACTIVE, null, null, null, null),
					PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
		assertThat(listed).extracting(ReviewItemResponse::username).containsExactly("alice", "rachel");
	}

	@Test
	void suspendedAccountsHaveTheirOwnSectionWithTheSuspensionAndItsNoteInTheRemarks() {
		user("rachel");
		AppUser alice = user("alice", "Finance");
		AppUser bob = user("bob");
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.LEFT_ORGANISATION, "resigned");
		this.lifecycle.suspend(bob.getPublicId(), ReasonCode.OTHER, null);
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		ReviewReportModel model = this.reportModels.build(reload(task), true, "rachel");

		assertThat(model.active().items()).extracting(ReviewReportModel.ItemRow::username).containsExactly("rachel");
		assertThat(model.suspended().summary()).isEqualTo(new ReviewReportModel.Summary(1, 0, 0, 1, 2));
		assertThat(model.suspended().items()).extracting(ReviewReportModel.ItemRow::username)
			.containsExactly("alice", "bob");
		ReviewReportModel.ItemRow alicesRow = model.suspended().items().get(0);
		assertThat(alicesRow.suspendedBy()).isEqualTo("admin");
		assertThat(alicesRow.suspensionReason()).isEqualTo("left_organisation");
		assertThat(alicesRow.remark()).isEqualTo("No changes; Suspension note: resigned");
		assertThat(alicesRow.createdAt()).isNotNull();
		assertThat(model.suspended().items().get(1).remark()).isNull();
	}

	/**
	 * Creates a privileged review in which alice is confirmed and bob is removed, and
	 * everyone else is still pending.
	 */
	private Task decidedTask() {
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "bob").getPublicId()), Decision.REMOVE,
				ReasonCode.NO_LONGER_REQUIRED, null);
		flushAndClear();
		return reload(task);
	}

}
