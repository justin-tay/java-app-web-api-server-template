package com.example.app.web.server.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.review.AccountReviewScheduler;
import com.example.commons.accounts.review.AccountReviewService;
import com.example.commons.accounts.review.AccountReviewService.ItemQuery;
import com.example.commons.accounts.review.AccountReviewService.PopulationQuery;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;

/**
 * Checks what a developer sees on a local start: with the {@code demo} context applied,
 * the scheduler creates the review task for the current month, which the demo makes a
 * review month, with an item for each of the 14 active sample accounts, and the two
 * populations list the 3 suspended accounts and the 2 accounts the sample data records as
 * already removed.
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
	void theSchedulerCreatesTheTaskAndTheTabsListTheSampleAccounts() {
		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(1);
		Task task = this.tasks.findAll().get(0);
		LocalDate today = LocalDate.now();
		assertThat(task.getStartDate()).isEqualTo(today.withDayOfMonth(1));
		assertThat(task.getDueDate()).isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
		assertThat(this.service.response(task).counts().pending()).isEqualTo(14L);

		assertThat(active(task)).hasSize(14)
			.extracting(ReviewItemResponse::username)
			.contains("account-reviewer-1", "olivia.chan", "kumar.raj", "jason.lee");
		assertThat(active(task)).filteredOn(row -> row.username().equals("olivia.chan"))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.department()).isEqualTo("Finance");
				assertThat(row.groups()).containsExactly("Users");
			});

		List<PopulationEntryResponse> suspended = population(task, ReviewPopulation.SUSPENDED);
		assertThat(suspended).extracting(PopulationEntryResponse::username)
			.containsExactlyInAnyOrder("farid.hassan", "alicia.wong", "benjamin.teo");
		assertThat(suspended).filteredOn(row -> row.username().equals("alicia.wong")).singleElement().satisfies(row -> {
			assertThat(row.reasonCode()).isEqualTo("left_organisation");
			assertThat(row.reasonNote()).startsWith("Resigned");
			assertThat(row.occurredAt()).isNotNull();
			assertThat(row.department()).isEqualTo("Finance");
		});

		List<PopulationEntryResponse> removed = population(task, ReviewPopulation.REMOVED);
		assertThat(removed).extracting(PopulationEntryResponse::username)
			.containsExactlyInAnyOrder("sarah.lim", "tom.yeo");
		assertThat(removed).extracting(PopulationEntryResponse::actor).containsExactlyInAnyOrder("hr.system", "system");
		assertThat(removed).extracting(PopulationEntryResponse::department).containsExactlyInAnyOrder("HR", "IT");
	}

	private List<ReviewItemResponse> active(Task task) {
		return this.service
			.items(task.getPublicId(), new ItemQuery(null, null, null, null),
					PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
	}

	private List<PopulationEntryResponse> population(Task task, ReviewPopulation population) {
		return this.service
			.population(task.getPublicId(), population, new PopulationQuery(null, null),
					PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
	}

}
