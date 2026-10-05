package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewOutcome;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.AccountReviewService.Decision;
import com.example.commons.accounts.review.AccountReviewService.ItemQuery;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.web.problem.BadRequestException;
import com.example.commons.web.problem.ConflictException;

/**
 * Tests {@link AccountReviewService} against the real schema: the scope of a task, the
 * active accounts, confirming, re-grouping and removing, a batch applying entirely or not
 * at all, the rule that a reviewer cannot review their own account, and what happens to
 * pending items when accounts change outside the review.
 */
@AccountsJpaTest
class AccountReviewServiceTest extends AccountReviewTestSupport {

	private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-00000000dead");

	private static final ItemQuery ALL = new ItemQuery(null, null, null, null);

	@Test
	void createsATaskWithAnItemForEveryActiveAccountOnly() {
		user("rachel");
		user("alice");
		AppUser carol = user("carol");
		carol.suspend(NOW, ReasonCode.OTHER, null);

		Task task = this.service.createTask(OCTOBER);

		assertThat(task.getType()).isEqualTo("account_review");
		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(this.items.findAll()).extracting(AccountReviewItem::getUsername)
			.containsExactlyInAnyOrder("rachel", "alice");
		assertThat(this.items.findAll()).extracting(AccountReviewItem::getOutcome)
			.containsOnly(AccountReviewOutcome.PENDING);
		assertThat(this.service.response(task).counts().pending()).isEqualTo(2);
		assertThat(this.auditEvents.findAll(Specification.unrestricted())).extracting(AccountAuditEvent::getAction)
			.contains("create_review_task");
	}

	@Test
	void aTaskWithNoActiveAccountsStaysOpenUntilBothPopulationsAreConfirmed() {
		Task task = this.service.createTask(OCTOBER);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
	}

	@Test
	void showsPendingRowsLiveNotWhatWasTrueWhenTheTaskWasCreated() {
		user("rachel");
		AppUser alice = user("alice", "Finance");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		AppUser live = this.users.findByPublicId(alice.getPublicId()).orElseThrow();
		live.update("Alice Tan", null, "Procurement");
		flushAndClear();

		ReviewItemResponse row = rows(task).stream()
			.filter(candidate -> candidate.username().equals("alice"))
			.findFirst()
			.orElseThrow();
		assertThat(row.name()).isEqualTo("Alice Tan");
		assertThat(row.department()).isEqualTo("Procurement");
		assertThat(row.groups()).containsExactly("users");
		assertThat(row.outcome()).isEqualTo("pending");
		assertThat(row.groupsBefore()).isNull();
		assertThat(row.ownAccount()).isFalse();
		assertThat(rows(task).stream().filter(ReviewItemResponse::ownAccount)).extracting(ReviewItemResponse::username)
			.containsExactly("rachel");
	}

	@Test
	void confirmingFreezesTheEvidenceAndIsAudited() {
		user("rachel");
		AppUser alice = user("alice", "Finance");
		alice.getGroups().add(this.entityManager.persist(new AppGroup("viewers")));
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.CONFIRMED);
		assertThat(item.getDecidedBy()).isEqualTo("rachel");
		assertThat(item.getDecidedAt()).isEqualTo(NOW);
		assertThat(item.getDepartment()).isEqualTo("Finance");
		assertThat(item.getGroupsBefore()).containsExactly("users", "viewers");
		assertThat(item.getGroupsAfter()).containsExactly("users", "viewers");
		assertThat(this.auditEvents.findAll(Specification.unrestricted())).extracting(AccountAuditEvent::getAction)
			.contains("confirm_review_item");
		ReviewItemResponse row = rows(task).stream()
			.filter(candidate -> candidate.username().equals("alice"))
			.findFirst()
			.orElseThrow();
		assertThat(row.remark()).isEqualTo("No changes");
		assertThat(this.service.response(task).progress().reviewed()).isEqualTo(1);
		assertThat(this.service.response(task).counts().confirmed()).isEqualTo(1);
	}

	@Test
	void removingThroughTheReviewMarksTheItemRemovedAndKeepsTheEvidence() {
		user("rachel");
		AppUser alice = user("alice", "HR");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.REMOVE,
				ReasonCode.LEFT_ORGANISATION, "resigned");
		flushAndClear();

		assertThat(this.users.findByPublicId(alice.getPublicId())).isEmpty();
		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.REMOVED);
		assertThat(item.getDecidedBy()).isEqualTo("rachel");
		assertThat(item.getDepartment()).isEqualTo("HR");
		assertThat(item.getGroupsBefore()).containsExactly("users");
		assertThat(item.getGroupsAfter()).isNull();
		AccountAuditEvent removal = this.auditEvents.findAllById(List.of(item.getRemovalAuditEventId())).get(0);
		assertThat(removal.getAction()).isEqualTo("delete_user");
		assertThat(removal.getReasonCode()).isEqualTo("left_organisation");
		assertThat(removal.getReasonNote()).isEqualTo("resigned");
		assertThat(removal.getDetails()).contains("\"department\":\"HR\"");
		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(this.service.response(task).counts().removed()).isEqualTo(1);
	}

	@Test
	void editingGroupsConfirmsTheItemAndRecordsTheGroupsBeforeAndAfter() {
		user("rachel");
		AppUser alice = user("alice");
		AppGroup viewers = this.entityManager.persist(new AppGroup("viewers"));
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		this.service.editGroups(task.getPublicId(), itemOf(task, "alice").getPublicId(), Set.of(viewers.getPublicId()));
		flushAndClear();

		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.CONFIRMED_GROUPS_EDITED);
		assertThat(item.getGroupsBefore()).containsExactly("users");
		assertThat(item.getGroupsAfter()).containsExactly("viewers");
		AppUser reloaded = this.users.findByPublicId(alice.getPublicId()).orElseThrow();
		assertThat(reloaded.getGroups()).extracting(AppGroup::getName).containsExactly("viewers");
		assertThat(this.auditEvents.findAll(Specification.unrestricted())).extracting(AccountAuditEvent::getAction)
			.contains("update_user", "edit_review_item_groups");
		ReviewItemResponse row = rows(task).stream()
			.filter(candidate -> candidate.username().equals("alice"))
			.findFirst()
			.orElseThrow();
		assertThat(row.remark()).isEqualTo("Added viewers; Removed users");
		assertThat(this.service.response(task).counts().confirmedGroupsEdited()).isEqualTo(1);
	}

	@Test
	void editingGroupsRejectsAnUnchangedSetAndAGroupTheReviewerCannotGrant() {
		user("rachel");
		user("alice");
		AppRole manage = this.entityManager.persist(new AppRole("USER_MANAGE"));
		AppGroup admins = new AppGroup("admins");
		admins.getRoles().add(manage);
		this.entityManager.persist(admins);
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		UUID aliceItem = itemOf(task, "alice").getPublicId();

		assertThatExceptionOfType(BadRequestException.class)
			.isThrownBy(() -> this.service.editGroups(task.getPublicId(), aliceItem, Set.of(this.group.getPublicId())));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service
			.editGroups(task.getPublicId(), aliceItem, Set.of(this.group.getPublicId(), admins.getPublicId())));
		assertThatExceptionOfType(com.example.commons.web.problem.ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.editGroups(task.getPublicId(), aliceItem, Set.of(UNKNOWN)));
		assertThat(itemOf(task, "alice").isPending()).isTrue();
		assertThat(this.service.assignableGroups()).extracting(summary -> summary.name()).containsExactly("users");
	}

	@Test
	void aReviewerCannotActOnTheirOwnAccount() {
		user("rachel");
		user("alice");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		UUID own = itemOf(task, "rachel").getPublicId();

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.decide(task.getPublicId(), List.of(own), Decision.CONFIRM, null, null));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.editGroups(task.getPublicId(), own, Set.of(this.group.getPublicId())));
		assertThat(itemOf(task, "rachel").isPending()).isTrue();
		assertThat(this.auditEvents.findAll(Specification.unrestricted())).extracting(AccountAuditEvent::getAction)
			.contains("review_rejected");
	}

	@Test
	void aBatchIsAppliedEntirelyOrNotAtAll() {
		user("rachel");
		user("alice");
		user("bob");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		UUID alice = itemOf(task, "alice").getPublicId();
		UUID bob = itemOf(task, "bob").getPublicId();
		this.service.decide(task.getPublicId(), List.of(alice), Decision.CONFIRM, null, null);
		flushAndClear();

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.decide(task.getPublicId(), List.of(bob, alice, UNKNOWN), Decision.CONFIRM,
					null, null))
			.withMessageContaining(alice.toString())
			.withMessageContaining(UNKNOWN.toString())
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(bob.toString()));
		flushAndClear();

		assertThat(itemOf(task, "bob").isPending()).isTrue();
	}

	@Test
	void aRemovalOutsideTheReviewMarksThePendingItemRemovedWithTheRemoverAsDecider() {
		user("rachel");
		AppUser alice = user("alice", "IT");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		authenticateAs("admin", "USER_MANAGE");
		this.lifecycle.remove(alice.getPublicId(), ReasonCode.NO_LONGER_REQUIRED, null);
		flushAndClear();

		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.REMOVED);
		assertThat(item.getDecidedBy()).isEqualTo("admin");
		assertThat(item.getDepartment()).isEqualTo("IT");
		assertThat(item.getRemovalAuditEventId()).isNotNull();
		assertThat(this.service.response(task).progress().total()).isEqualTo(1);
	}

	@Test
	void aSuspendedPendingAccountLeavesTheActiveListAndReturnsWhenUnsuspended() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();

		authenticateAs("admin", "USER_MANAGE");
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.POLICY_VIOLATION, null);
		flushAndClear();

		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(this.service.response(task).progress().total()).isEqualTo(1);
		assertThat(itemOf(task, "alice").isPending()).isTrue();

		this.lifecycle.unsuspend(alice.getPublicId());
		flushAndClear();

		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("alice", "rachel");
	}

	@Test
	void aDecidedItemStaysInTheActiveListWhenItsAccountIsLaterSuspended() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		authenticateAs("admin", "USER_MANAGE");
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.OTHER, null);
		flushAndClear();

		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("alice", "rachel");
		assertThat(itemOf(task, "alice").getOutcome()).isEqualTo(AccountReviewOutcome.CONFIRMED);
	}

	@Test
	void filtersAndSortsTheActiveAccounts() {
		user("rachel", "Compliance");
		user("alice", "Finance");
		user("bob", "Finance");
		user("carol", "HR");
		authenticateAs("rachel", "ACCOUNT_REVIEWER");
		Task task = this.service.createTask(OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "bob").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		assertThat(page(task, new ItemQuery(null, "finance", null, null), Sort.by("username").descending()))
			.extracting(ReviewItemResponse::username)
			.containsExactly("bob", "alice");
		assertThat(page(task, new ItemQuery(AccountReviewOutcome.CONFIRMED, null, null, null), Sort.by("username")))
			.extracting(ReviewItemResponse::username)
			.containsExactly("bob");
		assertThat(page(task, new ItemQuery(null, null, "USERS", "arol"), Sort.by("username")))
			.extracting(ReviewItemResponse::username)
			.containsExactly("carol");
		assertThat(page(task, ALL, Sort.by("department", "username"))).extracting(ReviewItemResponse::username)
			.containsExactly("rachel", "alice", "bob", "carol");
		assertThat(this.service.departments(task.getPublicId())).containsExactly("Compliance", "Finance", "HR");
	}

	@Test
	void summarisesOpenAndOverdueTasks() {
		user("alice");
		this.service.createTask(OCTOBER);
		this.service.createTask(new ReviewPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
		flushAndClear();

		assertThat(this.service.summary().openCount()).isEqualTo(2);
		assertThat(this.service.summary().earliestDueDate()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(this.service.summary().overdueCount()).isEqualTo(1);
	}

	private List<ReviewItemResponse> rows(Task task) {
		return page(task, ALL, Sort.by("username"));
	}

	private List<ReviewItemResponse> page(Task task, ItemQuery query, Sort sort) {
		return this.service.items(task.getPublicId(), query, PageRequest.of(0, 50, sort)).getContent();
	}

}
