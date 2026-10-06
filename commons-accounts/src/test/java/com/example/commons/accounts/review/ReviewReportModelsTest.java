package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.commons.accounts.AccountsJpaTest;
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

		assertThat(model.summary()).isEqualTo(new ReviewReportModel.Summary(1, 0, 1, 2, 4));
		assertThat(model.departments()).containsExactly(new ReviewReportModel.DepartmentRow("(none)", 0, 0, 0, 1, 1),
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

		assertThat(model.items()).extracting(ReviewReportModel.ItemRow::username)
			.containsExactly("alice", "bob", "rachel");
		assertThat(model.items()).extracting(ReviewReportModel.ItemRow::remark)
			.containsExactly("No changes", "Account removed (no_longer_required)", null);
		List<ReviewItemResponse> listed = this.service
			.items(task.getPublicId(), new ItemQuery(null, null, null, null),
					PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
		assertThat(listed).extracting(ReviewItemResponse::username).containsExactly("alice", "rachel");
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
