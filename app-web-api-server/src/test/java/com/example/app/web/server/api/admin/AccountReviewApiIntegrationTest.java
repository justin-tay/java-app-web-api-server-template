package com.example.app.web.server.api.admin;

import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.AccountReviewService;
import com.example.commons.accounts.review.ReviewPeriod;

/**
 * Tests the account review API against the seeded test database: only those who hold the
 * review permissions use it, a reviewer cannot review their own account, a batch is
 * applied entirely or not at all, a reviewer removes access and never grants it, the
 * review completes by itself and serves its report, and every change needs a recent login
 * (see docs/adr/0038). The seed has two account reviewers, {@code account-reviewer-1} and
 * {@code account-reviewer-2}, who can review each other. The privileged review covers the
 * two administrators, {@code admin} and {@code multi-group-user}, and the non-privileged
 * review the reviewers and {@code user}. Each test runs in a transaction that is rolled
 * back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountReviewApiIntegrationTest {

	private static final String ACCOUNT_REVIEWERS_ROLE_ID = "00000000-0000-0000-0000-000000000013";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountReviewService service;

	@Autowired
	private AccountReviewItemRepository items;

	@Autowired
	private AppRoleRepository roles;

	@Test
	void onlyThoseWithTheReviewPermissionsCanUseTheTaskAndReviewEndpoints() throws Exception {
		Task task = nonPrivilegedTask();

		this.mockMvc.perform(get("/tasks").with(loginAs("admin"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/tasks/summary").with(loginAs("user"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("admin")))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/tasks")).andExpect(status().isUnauthorized());
	}

	@Test
	void theDashboardListsTheNonPrivilegedTaskWithItsCountsAndProgressAndNoPopulations() throws Exception {
		Task task = nonPrivilegedTask();

		this.mockMvc.perform(get("/tasks").param("status", "open").with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(task.getPublicId().toString()))
			.andExpect(jsonPath("$.items[0].type").value("non_privileged_account_review"))
			.andExpect(jsonPath("$.items[0].status").value("open"))
			.andExpect(jsonPath("$.items[0].overdue").value(false))
			.andExpect(jsonPath("$.items[0].counts.pending").value(3))
			.andExpect(jsonPath("$.items[0].counts.confirmed").value(0))
			.andExpect(jsonPath("$.items[0].progress.reviewed").value(0))
			.andExpect(jsonPath("$.items[0].progress.total").value(3))
			.andExpect(jsonPath("$.items[0].populations").value(nullValue()))
			.andExpect(jsonPath("$.items[0].reportAvailable").value(false));
		this.mockMvc.perform(get("/tasks").param("status", "completed").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/tasks/summary").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.openCount").value(1))
			.andExpect(jsonPath("$.overdueCount").value(0))
			.andExpect(jsonPath("$.earliestDueDate").value(task.getDueDate().toString()));
	}

	@Test
	void thePrivilegedTaskCoversTheAdministratorsAndHasPopulations() throws Exception {
		Task task = privilegedTask();

		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("privileged_account_review"))
			.andExpect(jsonPath("$.counts.pending").value(2))
			.andExpect(jsonPath("$.populations.suspended.confirmed").value(false))
			.andExpect(jsonPath("$.populations.removed.confirmed").value(false));
		this.mockMvc.perform(get(items(task)).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("admin", "multi-group-user")))
			.andExpect(jsonPath("$.items[?(@.username == 'admin')].privilegedPermissions[0]")
				.value(hasItem("role:add-permission")));
	}

	@Test
	void aNonPrivilegedTaskHasNoPopulationsToReadOrConfirm() throws Exception {
		Task task = nonPrivilegedTask();

		this.mockMvc.perform(get(population(task, "removed")).with(loginAs("account-reviewer-1")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(confirmPopulation(task, "suspended", null, "account-reviewer-1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theTaskShowsTheActiveAccountsWithDepartmentAndRolesAndFlagsTheReviewersOwn() throws Exception {
		Task task = nonPrivilegedTask();

		this.mockMvc.perform(get(items(task)).with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.items[?(@.ownAccount == true)].username").value(hasItem("account-reviewer-1")))
			.andExpect(jsonPath("$.items[0].outcome").value("pending"));
		this.mockMvc.perform(get(items(task)).param("department", "Compliance").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.items[*].username")
				.value(containsInAnyOrder("account-reviewer-1", "account-reviewer-2")))
			.andExpect(jsonPath("$.items[0].department").value("Compliance"))
			.andExpect(jsonPath("$.items[0].roles[0]").value("Account Reviewers"))
			.andExpect(jsonPath("$.items[0].currentRoles[0].name").value("Account Reviewers"))
			.andExpect(jsonPath("$.items[0].currentRoles[0].id").value(ACCOUNT_REVIEWERS_ROLE_ID))
			.andExpect(jsonPath("$.items[0].privilegedPermissions").value(nullValue()));
		this.mockMvc.perform(get(items(task)).param("outcome", "removed").with(loginAs("account-reviewer-1")))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/departments")
				.with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$").value(containsInAnyOrder("Compliance", "Finance")));
	}

	@Test
	void aReviewerConfirmsAnotherReviewersAccountButNotTheirOwn() throws Exception {
		Task task = nonPrivilegedTask();
		UUID own = itemId(task, "account-reviewer-1");
		UUID other = itemId(task, "account-reviewer-2");

		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + own + "\",\"" + other + "\"],\"decision\":\"confirm\"}",
					"account-reviewer-1"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(
					decisions(task, "{\"itemIds\":[\"" + other + "\"],\"decision\":\"confirm\"}", "account-reviewer-1"))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.counts.confirmed").value(1))
			.andExpect(jsonPath("$.counts.pending").value(2))
			.andExpect(jsonPath("$.progress.reviewed").value(1));
		this.mockMvc.perform(get(items(task)).param("outcome", "confirmed").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("account-reviewer-2")))
			.andExpect(jsonPath("$.items[0].decidedBy").value("account-reviewer-1"))
			.andExpect(jsonPath("$.items[0].remark").value("No changes"));
	}

	@Test
	void aRemovalNeedsAReasonAndTheRemovedAccountLeavesTheActiveListForTheRemovedPopulation() throws Exception {
		Task task = nonPrivilegedTask();
		Task privileged = privilegedTask();
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

		this.mockMvc.perform(get(items(task)).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.totalItems").value(2));
		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.counts.removed").value(1))
			.andExpect(jsonPath("$.progress.total").value(2));
		this.mockMvc.perform(get(population(privileged, "removed")).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].username").value("user"))
			.andExpect(jsonPath("$.items[0].name").value("Mary Goh"))
			.andExpect(jsonPath("$.items[0].department").value("Finance"))
			.andExpect(jsonPath("$.items[0].reasonCode").value("left_organisation"))
			.andExpect(jsonPath("$.items[0].reasonNote").value("moved teams"))
			.andExpect(jsonPath("$.items[0].actor").value("account-reviewer-1"));
		this.mockMvc.perform(get("/audit-events").param("action", "remove_review_item").with(loginAs("admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].actor").value("account-reviewer-1"))
			.andExpect(jsonPath("$.items[0].targetName").value("user"));
	}

	@Test
	void aReviewerRemovesRolesFromAnAccountWhichConfirmsItButCannotAddARole() throws Exception {
		Task privileged = privilegedTask();
		Task task = nonPrivilegedTask();
		UUID multiRole = itemId(privileged, "multi-group-user");
		UUID user = itemId(task, "user");
		UUID users = roleId("Users");
		UUID administrators = roleId("Administrators");

		this.mockMvc.perform(editRoles(task, user, "{\"roleIds\":[\"" + administrators + "\"]}", "account-reviewer-1"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(editRoles(task, user, "{\"roleIds\":[\"" + users + "\",\"" + administrators + "\"]}",
					"account-reviewer-1"))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(editRoles(task, user, "{\"roleIds\":[]}", "account-reviewer-1"))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(editRoles(task, user, "{\"roleIds\":[\"" + users + "\"]}", "account-reviewer-1"))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(editRoles(privileged, multiRole, "{\"roleIds\":[\"" + users + "\"]}", "account-reviewer-1"))
			.andExpect(status().isNoContent());

		this.mockMvc
			.perform(get(items(privileged)).param("outcome", "confirmed_roles_edited")
				.with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.items[0].username").value("multi-group-user"))
			.andExpect(jsonPath("$.items[0].roles[0]").value("Users"))
			.andExpect(jsonPath("$.items[0].rolesBefore[0]").value("Administrators"))
			.andExpect(jsonPath("$.items[0].currentRoles").value(nullValue()))
			.andExpect(jsonPath("$.items[0].remark").value("Removed Administrators"));
	}

	@Test
	void aUserWithoutTheReviewPermissionsCannotDecide() throws Exception {
		Task task = nonPrivilegedTask();

		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + itemId(task, "user") + "\"],\"decision\":\"confirm\"}",
					"admin"))
			.andExpect(status().isForbidden());
	}

	@Test
	void thePrivilegedReviewCompletesWhenTheWorkIsDoneAndServesItsReportAndThenIsReadOnly() throws Exception {
		Task task = privilegedTask();
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/report").param("format", "csv")
				.with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition",
					startsWith("attachment; filename=\"privileged-account-review-")))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("-draft.csv")));

		this.mockMvc
			.perform(confirmPopulation(task, "suspended", "{\"note\":\"none suspended\"}", "account-reviewer-1"))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(confirmPopulation(task, "suspended", null, "account-reviewer-1"))
			.andExpect(status().isConflict());
		this.mockMvc.perform(confirmPopulation(task, "removed", null, "account-reviewer-1"))
			.andExpect(status().isNoContent());
		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + itemId(task, "admin") + "\"],\"decision\":\"confirm\"}",
					"account-reviewer-1"))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.status").value("open"))
			.andExpect(jsonPath("$.populations.suspended.confirmedBy").value("account-reviewer-1"));

		this.mockMvc
			.perform(decisions(task,
					"{\"itemIds\":[\"" + itemId(task, "multi-group-user") + "\"],\"decision\":\"confirm\"}",
					"account-reviewer-2"))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.status").value("completed"))
			.andExpect(jsonPath("$.completedBy").value("account-reviewer-2"))
			.andExpect(jsonPath("$.reportAvailable").value(true));
		this.mockMvc.perform(get("/tasks/summary").with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.openCount").value(0));
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/report").param("format", "pdf")
				.with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andExpect(header().string("Content-Disposition",
					org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("draft"))));
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/report").param("format", "xlsx")
				.with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk());
		this.mockMvc
			.perform(get("/account-reviews/tasks/" + task.getPublicId() + "/report").param("format", "bogus")
				.with(loginAs("account-reviewer-1")))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(decisions(task, "{\"itemIds\":[\"" + itemId(task, "admin") + "\"],\"decision\":\"confirm\"}",
					"account-reviewer-1"))
			.andExpect(status().isConflict());
		this.mockMvc.perform(get("/audit-events").param("action", "export_review_report").with(loginAs("admin")))
			.andExpect(jsonPath("$.totalItems").value(3));
		this.mockMvc.perform(get("/audit-events").param("action", "complete_review_task").with(loginAs("admin")))
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].details.sha256").isString());
	}

	@Test
	void theNonPrivilegedReviewCompletesWithoutPopulations() throws Exception {
		Task task = nonPrivilegedTask();

		this.mockMvc
			.perform(decisions(task,
					"{\"itemIds\":[\"" + itemId(task, "user") + "\",\"" + itemId(task, "account-reviewer-2")
							+ "\"],\"decision\":\"confirm\"}",
					"account-reviewer-1"))
			.andExpect(status().isNoContent());
		this.mockMvc
			.perform(decisions(task,
					"{\"itemIds\":[\"" + itemId(task, "account-reviewer-1") + "\"],\"decision\":\"confirm\"}",
					"account-reviewer-2"))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/account-reviews/tasks/" + task.getPublicId()).with(loginAs("account-reviewer-1")))
			.andExpect(jsonPath("$.status").value("completed"))
			.andExpect(jsonPath("$.reportAvailable").value(true));
	}

	@Test
	void aChangeNeedsARecentLogin() throws Exception {
		Task task = nonPrivilegedTask();
		UUID user = itemId(task, "user");

		this.mockMvc
			.perform(post("/account-reviews/tasks/" + task.getPublicId() + "/decisions")
				.with(oidcLoginAs("account-reviewer-1", Instant.now().minus(Duration.ofHours(1))))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"" + user + "\"],\"decision\":\"confirm\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:problem:reauthentication-required"));
		this.mockMvc
			.perform(post(population(task, "suspended") + "/confirmation")
				.with(oidcLoginAs("account-reviewer-1", Instant.now().minus(Duration.ofHours(1))))
				.with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void theAuditTrailIsReadableByWhoeverHoldsTheAuditReadPermissionOnly() throws Exception {
		nonPrivilegedTask();

		this.mockMvc.perform(get("/audit-events").param("targetType", "REVIEW").with(loginAs("account-reviewer-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].action").value("create_review_task"))
			.andExpect(jsonPath("$.items[0].details.itemCount").value(3))
			.andExpect(jsonPath("$.items[0].details.type").value("non_privileged_account_review"));
		this.mockMvc.perform(get("/audit-events").with(loginAs("admin"))).andExpect(status().isOk());
		this.mockMvc.perform(get("/audit-events").with(loginAs("user"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/audit-events").param("targetType", "BOGUS").with(loginAs("admin")))
			.andExpect(status().isBadRequest());
	}

	private Task privilegedTask() {
		return this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, period());
	}

	private Task nonPrivilegedTask() {
		return this.service.createTask(Task.NON_PRIVILEGED_ACCOUNT_REVIEW, period());
	}

	private static ReviewPeriod period() {
		return ReviewPeriod.containing(LocalDate.now(), 1).orElseThrow();
	}

	private UUID roleId(String name) {
		return this.roles.findAll()
			.stream()
			.filter(role -> role.getName().equals(name))
			.findFirst()
			.orElseThrow()
			.getPublicId();
	}

	private UUID itemId(Task task, String username) {
		return this.items.findAll()
			.stream()
			.filter(item -> item.getTaskId().equals(task.getId()) && item.getUsername().equals(username))
			.findFirst()
			.orElseThrow()
			.getPublicId();
	}

	private static String items(Task task) {
		return "/account-reviews/tasks/" + task.getPublicId() + "/items";
	}

	private static String population(Task task, String population) {
		return "/account-reviews/tasks/" + task.getPublicId() + "/populations/" + population;
	}

	private MockHttpServletRequestBuilder decisions(Task task, String body, String reviewer) {
		return post("/account-reviews/tasks/" + task.getPublicId() + "/decisions").with(loginAs(reviewer))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private MockHttpServletRequestBuilder editRoles(Task task, UUID item, String body, String reviewer) {
		return put(items(task) + "/" + item + "/roles").with(loginAs(reviewer))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private MockHttpServletRequestBuilder confirmPopulation(Task task, String population, String body,
			String reviewer) {
		MockHttpServletRequestBuilder request = post(population(task, population) + "/confirmation")
			.with(loginAs(reviewer))
			.with(csrf());
		return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static RequestPostProcessor loginAs(String username) {
		return oidcLoginAs(username, Instant.now());
	}

}
