package com.example.app.web.server.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.AccountReviewScheduler;
import com.example.commons.accounts.review.AccountReviewService;
import com.example.commons.accounts.review.AccountReviewService.Category;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.accounts.domain.TaskRepository;

/**
 * Checks what a developer sees on a local start: with the {@code demo} context applied,
 * the scheduler creates the review task for the current window with an item for every
 * sample account, and the three categories list them as 14 active, 3 suspended, and the 2
 * accounts the sample data records as already removed.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=dev,demo")
@ActiveProfiles("test")
@Transactional
class DemoSampleDataReviewIntegrationTest {

	@Autowired
	private AccountReviewScheduler scheduler;

	@Autowired
	private AccountReviewService service;

	@Autowired
	private TaskRepository tasks;

	@Test
	void theSchedulerCreatesTheTaskAndTheCategoriesListTheSampleAccounts() {
		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		Task task = this.tasks.findAll().get(0);
		LocalDate today = LocalDate.now();
		assertThat(task.getStartDate()).isBeforeOrEqualTo(today);
		assertThat(task.getDueDate()).isAfterOrEqualTo(today);
		assertThat(this.service.response(task).counts()).containsEntry("pending_verification", 17L);

		assertThat(rows(task, Category.ACTIVE)).hasSize(14)
			.extracting(ReviewItemResponse::username)
			.contains("account-reviewer-1", "olivia.chan", "kumar.raj", "jason.lee");
		assertThat(rows(task, Category.SUSPENDED)).extracting(ReviewItemResponse::username)
			.containsExactlyInAnyOrder("farid.hassan", "alicia.wong", "benjamin.teo");
		assertThat(rows(task, Category.SUSPENDED)).filteredOn(row -> row.username().equals("alicia.wong"))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.reasonCode()).isEqualTo("left_organisation");
				assertThat(row.reasonNote()).startsWith("Resigned");
				assertThat(row.suspendedAt()).isNotNull();
			});
		assertThat(rows(task, Category.REMOVED)).extracting(ReviewItemResponse::username)
			.containsExactlyInAnyOrder("sarah.lim", "tom.yeo");
		assertThat(rows(task, Category.REMOVED)).extracting(ReviewItemResponse::removedBy)
			.containsExactlyInAnyOrder("hr.system", "system");
	}

	private java.util.List<ReviewItemResponse> rows(Task task, Category category) {
		return this.service.items(task.getId(), category, null, null, PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
	}

}
