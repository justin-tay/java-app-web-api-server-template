package com.example.app.web.server.api.admin;

import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.AccountReviewService;
import com.example.commons.accounts.review.ReviewPeriod;
import com.example.commons.audit.AuditOutcome;
import com.example.commons.audit.AuditQuery;
import com.example.commons.audit.AuditTrail;

/**
 * Tests that a refused review action stays in the audit trail although the request that
 * attempted it is rolled back (see docs/adr/0040). Unlike the other API tests, this one
 * runs without a test transaction, because a transaction around the test would hide a row
 * that the request's own rollback removes. What it commits is deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewRefusalAuditIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountReviewService service;

	@Autowired
	private AccountReviewItemRepository items;

	@Autowired
	private AuditTrail trail;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Instant start;

	private Task task;

	@BeforeEach
	void createTask() {
		this.start = Instant.now();
		this.task = this.service.createTask(Task.NON_PRIVILEGED_ACCOUNT_REVIEW,
				ReviewPeriod.containing(LocalDate.now(), 1).orElseThrow());
	}

	@AfterEach
	void deleteWhatTheTestCommitted() {
		this.jdbcTemplate.update("DELETE FROM account_review_item WHERE task_id = ?", this.task.getId());
		this.jdbcTemplate.update("DELETE FROM task WHERE id = ?", this.task.getId());
		this.jdbcTemplate.update("DELETE FROM audit_event WHERE occurred_at >= ?", Timestamp.from(this.start));
	}

	@Test
	void aReviewersAttemptOnTheirOwnAccountIsRecordedAsAFailure() throws Exception {
		UUID own = this.items.findAll()
			.stream()
			.filter(item -> item.getTaskId().equals(this.task.getId())
					&& item.getUsername().equals("account-reviewer-1"))
			.findFirst()
			.orElseThrow()
			.getPublicId();

		this.mockMvc
			.perform(post("/account-reviews/tasks/" + this.task.getPublicId() + "/decisions")
				.with(oidcLoginAs("account-reviewer-1", Instant.now()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"" + own + "\"],\"decision\":\"confirm\"}"))
			.andExpect(status().isForbidden());

		assertThat(this.trail.find(AuditQuery.where().anyOutcome().targetIds(List.of(own.toString())))).singleElement()
			.satisfies(event -> {
				assertThat(event.action()).isEqualTo("confirm_review_item");
				assertThat(event.outcome()).isEqualTo(AuditOutcome.FAILURE);
				assertThat(event.reasonCode()).isEqualTo("own_account");
				assertThat(event.actor()).isEqualTo("account-reviewer-1");
			});
		this.mockMvc
			.perform(get("/audit-events").param("outcome", "failure")
				.param("action", "confirm_review_item")
				.with(oidcLoginAs("account-reviewer-2", Instant.now())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].targetId", hasItem(own.toString())))
			.andExpect(jsonPath("$.items[*].outcome", everyItem(is("failure"))));
	}

}
