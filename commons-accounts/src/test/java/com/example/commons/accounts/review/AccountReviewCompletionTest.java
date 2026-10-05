package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountReviewReport;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.AccountReviewReports.Download;
import com.example.commons.accounts.review.AccountReviewReports.Format;
import com.example.commons.accounts.review.AccountReviewService.Decision;
import com.example.commons.web.problem.ConflictException;

/**
 * Tests that a task completes by itself when the work is done, stores its PDF once, and
 * is then read-only, and the report downloads.
 */
@AccountsJpaTest
class AccountReviewCompletionTest extends AccountReviewTestSupport {

	@Test
	void aReviewersOwnPendingItemKeepsTheTaskOpenUntilAnotherReviewerDecidesIt() {
		user("rachel");
		user("ravi");
		user("alice");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId(), itemOf(task, "ravi").getPublicId()), Decision.CONFIRM,
				null, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();

		assertThat(this.service.response(reload(task)).status()).isEqualTo("open");
		assertThat(this.service.response(reload(task)).progress().reviewed()).isEqualTo(2);
		assertThat(this.service.response(reload(task)).progress().total()).isEqualTo(3);

		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "rachel").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		assertThat(this.service.response(reload(task)).status()).isEqualTo("completed");
		assertThat(this.service.response(reload(task)).completedBy()).isEqualTo("ravi");
	}

	@Test
	void editingGroupsCanBeTheLastActionThatCompletesTheTask() {
		user("rachel");
		user("alice");
		AppGroup viewers = this.entityManager.persist(new AppGroup("viewers"));
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "rachel").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();

		this.service.editGroups(task.getPublicId(), itemOf(task, "alice").getPublicId(), Set.of(viewers.getPublicId()));
		flushAndClear();

		assertThat(this.service.response(reload(task)).status()).isEqualTo("completed");
		assertThat(this.service.response(reload(task)).counts().confirmedGroupsEdited()).isEqualTo(1);
	}

	@Test
	void doesNotCompleteWhileAnItemIsPendingOrAPopulationIsUnconfirmed() {
		user("rachel");
		user("alice");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		flushAndClear();

		assertThat(this.service.response(reload(task)).status()).isEqualTo("open");
		assertThat(this.storedReports.existsByTaskId(task.getId())).isFalse();
	}

	@Test
	void theLastActionCompletesTheTaskRecordsTheCompleterAndStoresTheReportOnce() throws Exception {
		user("rachel", "Compliance");
		user("alice", "Finance");
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId(), itemOf(task, "rachel").getPublicId()), Decision.CONFIRM,
				null, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		flushAndClear();
		assertThat(this.tasks.findByPublicId(task.getPublicId()).orElseThrow().isOpen()).isTrue();

		this.clock.set(NOW.plusSeconds(60));
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, "none this period");
		flushAndClear();

		Task completed = this.tasks.findByPublicId(task.getPublicId()).orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(completed.getCompletedBy()).isEqualTo("ravi");
		assertThat(completed.getCompletedAt()).isEqualTo(NOW.plusSeconds(60));
		AccountReviewReport report = this.storedReports.findByTaskId(task.getId()).orElseThrow();
		assertThat(report.getGeneratedBy()).isEqualTo("ravi");
		assertThat(new String(report.getContent(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(report.getSizeBytes()).isEqualTo(report.getContent().length);
		assertThat(report.getSha256())
			.isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(report.getContent())));
		assertThat(this.auditEvents.findAll(Specification.unrestricted()))
			.filteredOn(event -> event.getAction().equals("complete_review_task"))
			.singleElement()
			.satisfies(event -> assertThat(event.getDetails()).contains(report.getSha256()));
		assertThat(this.service.response(reload(task)).reportAvailable()).isTrue();
	}

	@Test
	void aCompletedTaskRejectsEveryChange() {
		user("rachel");
		user("alice");
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = completed("alice", "rachel");

		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null, null));
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.editGroups(task.getPublicId(),
				itemOf(task, "alice").getPublicId(), Set.of(this.group.getPublicId())));
		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null));
	}

	@Test
	void aTaskWithNoActiveAccountsCompletesOnceBothPopulationsAreConfirmed() {
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);

		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(this.storedReports.existsByTaskId(task.getId())).isTrue();
	}

	@Test
	void theJobCompletesATaskThatAnOutsideRemovalFinishedWithTheSystemAsCompleter() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "rachel").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();
		assertThat(this.tasks.findByPublicId(task.getPublicId()).orElseThrow().isOpen()).isTrue();

		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		this.lifecycle.remove(alice.getPublicId(), ReasonCode.INACTIVE_ACCOUNT, null);
		flushAndClear();
		assertThat(this.service.openTaskIds()).containsExactly(task.getPublicId());

		this.service.completeIfFinished(task.getPublicId());
		flushAndClear();

		Task completed = this.tasks.findByPublicId(task.getPublicId()).orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(completed.getCompletedBy()).isEqualTo("system");
		assertThat(this.storedReports.existsByTaskId(task.getId())).isTrue();
		assertThat(this.service.openTaskIds()).isEmpty();
	}

	@Test
	void aDraftDownloadIsMarkedNotStoredAndAudited() {
		user("rachel");
		user("alice");
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		Download draft = this.service.download(task.getPublicId(), Format.CSV);
		Download pdf = this.service.download(task.getPublicId(), Format.PDF);
		Download xlsx = this.service.download(task.getPublicId(), Format.XLSX);

		assertThat(draft.draft()).isTrue();
		assertThat(pdf.draft()).isTrue();
		assertThat(new String(draft.content(), StandardCharsets.UTF_8)).startsWith("﻿\"No.\",\"Name\"")
			.contains("\"alice\"", "\"Pending\"");
		assertThat(new String(pdf.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(new String(xlsx.content(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
		assertThat(this.storedReports.existsByTaskId(task.getId())).isFalse();
		assertThat(this.auditEvents.findAll(Specification.unrestricted()))
			.filteredOn(event -> event.getAction().equals("export_review_report"))
			.extracting(AccountAuditEvent::getDetails)
			.allSatisfy(details -> assertThat(details).contains("\"draft\":true"))
			.hasSize(3);
	}

	@Test
	void aCompletedTaskAlwaysReturnsTheStoredPdfAndGeneratesTheOtherFormatsFromFrozenData() {
		user("rachel", "Compliance");
		user("alice", "Finance");
		authenticateAs("ravi", "ACCOUNT_REVIEWER");
		Task task = completed("alice", "rachel");
		byte[] stored = this.storedReports.findByTaskId(task.getId()).orElseThrow().getContent();

		this.clock.set(NOW.plusSeconds(3600));
		Download pdf = this.service.download(task.getPublicId(), Format.PDF);
		Download csv = this.service.download(task.getPublicId(), Format.CSV);

		assertThat(pdf.draft()).isFalse();
		assertThat(pdf.content()).isEqualTo(stored);
		assertThat(csv.draft()).isFalse();
		String text = new String(csv.content(), StandardCharsets.UTF_8);
		assertThat(text).contains("\"alice\"").contains("\"Confirmed\"").doesNotContain("Pending");
	}

	/**
	 * Completes a task of the given users, all confirmed, with both populations
	 * confirmed.
	 */
	private Task completed(String... usernames) {
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(),
				java.util.Arrays.stream(usernames).map(username -> itemOf(task, username).getPublicId()).toList(),
				Decision.CONFIRM, null, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.SUSPENDED, null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();
		assertThat(this.tasks.findByPublicId(task.getPublicId()).orElseThrow().getStatus())
			.isEqualTo(TaskStatus.COMPLETED);
		return this.tasks.findByPublicId(task.getPublicId()).orElseThrow();
	}

}
