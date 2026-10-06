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
 * the scheduler creates both review tasks for the current month, which the demo makes a
 * review month for each. The privileged review has an item for each of the 2
 * administrators and the non-privileged review one for each of the 12 other active sample
 * accounts. The sample data's suspended and removed accounts are all non-privileged, so
 * the non-privileged review's two populations list the 3 suspended accounts and the 2
 * accounts the sample data records as already removed, and the privileged review's are
 * empty.
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
	void theSchedulerCreatesTheTasksAndTheTabsListTheSampleAccounts() {
		this.scheduler.run();

		assertThat(this.tasks.findAll()).hasSize(2);
		Task privileged = task(Task.PRIVILEGED_ACCOUNT_REVIEW);
		Task nonPrivileged = task(Task.NON_PRIVILEGED_ACCOUNT_REVIEW);
		LocalDate today = LocalDate.now();
		assertThat(privileged.getStartDate()).isEqualTo(today.withDayOfMonth(1));
		assertThat(nonPrivileged.getDueDate()).isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
		assertThat(this.service.response(privileged).counts().pending()).isEqualTo(2L);
		assertThat(this.service.response(nonPrivileged).counts().pending()).isEqualTo(12L);

		assertThat(active(privileged)).extracting(ReviewItemResponse::username)
			.containsExactlyInAnyOrder("admin", "multi-group-user");
		assertThat(active(nonPrivileged)).hasSize(12)
			.extracting(ReviewItemResponse::username)
			.contains("account-reviewer-1", "olivia.chan", "kumar.raj", "jason.lee");
		assertThat(active(nonPrivileged)).filteredOn(row -> row.username().equals("olivia.chan"))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.department()).isEqualTo("Finance");
				assertThat(row.roles()).containsExactly("Users");
			});

		assertThat(population(privileged, ReviewPopulation.SUSPENDED)).isEmpty();
		assertThat(population(privileged, ReviewPopulation.REMOVED)).isEmpty();
		List<PopulationEntryResponse> suspended = population(nonPrivileged, ReviewPopulation.SUSPENDED);
		assertThat(suspended).extracting(PopulationEntryResponse::username)
			.containsExactlyInAnyOrder("farid.hassan", "alicia.wong", "benjamin.teo");
		assertThat(suspended).filteredOn(row -> row.username().equals("alicia.wong")).singleElement().satisfies(row -> {
			assertThat(row.reasonCode()).isEqualTo("left_organisation");
			assertThat(row.reasonNote()).startsWith("Resigned");
			assertThat(row.occurredAt()).isNotNull();
			assertThat(row.department()).isEqualTo("Finance");
			assertThat(row.actor()).isEqualTo("admin");
		});
		assertThat(suspended).extracting(PopulationEntryResponse::actor)
			.containsExactlyInAnyOrder("system", "admin", "admin");

		List<PopulationEntryResponse> removed = population(nonPrivileged, ReviewPopulation.REMOVED);
		assertThat(removed).extracting(PopulationEntryResponse::username)
			.containsExactlyInAnyOrder("sarah.lim", "tom.yeo");
		assertThat(removed).extracting(PopulationEntryResponse::actor).containsExactlyInAnyOrder("hr.system", "system");
		assertThat(removed).extracting(PopulationEntryResponse::department).containsExactlyInAnyOrder("HR", "IT");
	}

	private Task task(String type) {
		return this.tasks.findAll().stream().filter(task -> task.getType().equals(type)).findFirst().orElseThrow();
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
