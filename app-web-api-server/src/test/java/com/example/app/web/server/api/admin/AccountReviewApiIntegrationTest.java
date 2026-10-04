package com.example.app.web.server.api.admin;

import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.domain.ReviewItemRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.AccountReviewService;
import com.example.commons.accounts.review.ReviewWindow;

/**
 * Tests the account review API against the seeded test database: only account reviewers
 * use it, a reviewer cannot review their own account, a batch is applied entirely or not
 * at all, and every change needs a recent login. The seed has two account reviewers,
 * {@code account-reviewer-1} and {@code account-reviewer-2}, who can review each other.
 * Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountReviewApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountReviewService service;

	@Autowired
	private ReviewItemRepository items;

	@Test
	void onlyAccountReviewersCanUseTheTaskAndReviewEndpoints() throws Exception {
		Task task = createTask();

		this.mockMvc.perform(get("/tasks").with(loginAs("admin"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/tasks/summary").with(loginAs("user"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("admin")))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/tasks")).andExpect(status().isUnauthorized());
	}

	@Test
	void theDashboardListsTheTaskWithItsCountsAndTheSummaryShowsItIsOpen() throws Exception {
		Task task = createTask();

		this.mockMvc.perform(get("/tasks").param("status", "open").with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(task.getPublicId().toString()))
			.andExpect(jsonPath("$.items[0].type").value("account_review"))
			.andExpect(jsonPath("$.items[0].status").value("open"))
			.andExpect(jsonPath("$.items[0].overdue").value(false))
			.andExpect(jsonPath("$.items[0].counts.pending_verification").value(5))
			.andExpect(jsonPath("$.items[0].counts.verified").value(0));
		this.mockMvc.perform(get("/tasks").param("status", "completed").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/tasks/summary").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.openCount").value(1))
			.andExpect(jsonPath("$.overdueCount").value(0))
			.andExpect(jsonPath("$.earliestDueDate").value(task.getDueDate().toString()));
	}

	@Test
	void theTaskShowsEachAccountInItsCategoryAndFlagsTheReviewersOwn() throws Exception {
		Task task = createTask();

		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/items").param("category", "active")
				.with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(5))
			.andExpect(jsonPath("$.items[?(@.ownAccount == true)].username").value(hasItem("account-reviewer-1")))
			.andExpect(jsonPath("$.items[0].reviewStatus").value("pending_verification"));
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/items").param("category", "suspended")
				.with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/items").param("category", "bogus")
				.with(loginAs("account-reviewer-1")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aReviewerVerifiesAnotherReviewersAccountButNotTheirOwn() throws Exception {
		Task task = createTask();
		UUID own = itemId(task, "account-reviewer-1");
		UUID other = itemId(task, "account-reviewer-2");

		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + own + "\",\"" + other + "\"],\"decision\":\"verify\"}",
					"account-reviewer-1"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + other + "\"],\"decision\":\"verify\"}", "account-reviewer-1"))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/tasks/" + "summary").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.openCount").value(1));
		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.counts.verified").value(1))
			.andExpect(jsonPath("$.counts.pending_verification").value(4));
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/items").param("category", "active")
				.param("reviewStatus", "verified")
				.with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("account-reviewer-2")))
			.andExpect(jsonPath("$.items[0].decidedBy").value("account-reviewer-1"));
	}

	@Test
	void aRemovalNeedsAReasonAndDeletesTheAccountButTheRemovedCategoryStillShowsIt() throws Exception {
		Task task = createTask();
		UUID user = itemId(task, "user");

		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + user + "\"],\"decision\":\"remove\"}", "account-reviewer-1"))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(decisions(task,
					"{\"itemIds\":[\"" + user + "\"],\"decision\":\"remove\",\"reasonCode\":\"left_organisation\","
							+ "\"note\":\"moved teams\"}",
					"account-reviewer-1"))
			.andExpect(status().isNoContent());

		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/items").param("category", "removed")
				.with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].username").value("user"))
			.andExpect(jsonPath("$.items[0].name").value("Mary Goh"))
			.andExpect(jsonPath("$.items[0].reasonCode").value("left_organisation"))
			.andExpect(jsonPath("$.items[0].reasonNote").value("moved teams"))
			.andExpect(jsonPath("$.items[0].removedBy").value("account-reviewer-1"));
		this.mockMvc.perform(get("/audit-events").param("action", "remove_review_item").with(loginAs("admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].actor").value("account-reviewer-1"))
			.andExpect(jsonPath("$.items[0].targetName").value("user"));
	}

	@Test
	void suspendingFromAnItemRecordsTheReasonAndLeavesTheItemPending() throws Exception {
		Task task = createTask();
		UUID user = itemId(task, "user");

		this.mockMvc
			.perform(post("/account-reviews/tasks/" + task.getPublicId() + "/items/" + user + "/suspend")
				.with(loginAs("account-reviewer-1"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"policy_violation\"}"))
			.andExpect(status().isNoContent());

		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/items").param("category", "suspended")
				.with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.items[0].username").value("user"))
			.andExpect(jsonPath("$.items[0].reasonCode").value("policy_violation"))
			.andExpect(jsonPath("$.items[0].suspendedAt").exists())
			.andExpect(jsonPath("$.items[0].reviewStatus").value("pending_verification"));
	}

	@Test
	void aChangeNeedsARecentLogin() throws Exception {
		Task task = createTask();
		UUID user = itemId(task, "user");

		this.mockMvc
			.perform(post("/account-reviews/tasks/" + task.getPublicId() + "/decisions")
				.with(oidcLoginAs("account-reviewer-1", Instant.now().minus(Duration.ofHours(1))))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"" + user + "\"],\"decision\":\"verify\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:problem:reauthentication-required"));
	}

	@Test
	void theAuditTrailIsReadableByReviewersAndUserAdministratorsOnly() throws Exception {
		createTask();

		this.mockMvc.perform(get("/audit-events").param("targetType", "REVIEW").with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].action").value("create_review_task"))
			.andExpect(jsonPath("$.items[0].details.itemCount").value(5));
		this.mockMvc.perform(get("/audit-events").with(loginAs("admin"))).andExpect(status().isOk());
		this.mockMvc.perform(get("/audit-events").with(loginAs("user"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/audit-events").param("targetType", "BOGUS").with(loginAs("admin")))
			.andExpect(status().isBadRequest());
	}

	private Task createTask() {
		return this.service.createTask(ReviewWindow.containing(LocalDate.now(), 3));
	}

	private UUID itemId(Task task, String username) {
		return this.items.findAll()
			.stream()
			.filter(item -> item.getTaskId().equals(task.getId()) && item.getUsername().equals(username))
			.findFirst()
			.orElseThrow()
			.getPublicId();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decisions(Task task, String body,
			String reviewer) {
		return post("/account-reviews/tasks/" + task.getPublicId() + "/decisions").with(loginAs(reviewer))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private static RequestPostProcessor loginAs(String username) {
		return oidcLoginAs(username, Instant.now());
	}

}
