package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import com.example.commons.audit.AuditOutcome;
import com.example.commons.audit.AuditQuery;
import com.example.commons.audit.AuditRecord;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.domain.AccountReviewCategory;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewOutcome;
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

	private static final ItemQuery ALL = new ItemQuery(AccountReviewCategory.ACTIVE, null, null, null, null);

	@Test
	void createsAPrivilegedTaskWithAnItemForEveryActiveAndSuspendedPrivilegedAccountOnly() {
		user("rachel");
		user("alice");
		AppUser carol = user("carol");
		carol.suspend(NOW, ReasonCode.OTHER, null);

		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);

		assertThat(task.getType()).isEqualTo("privileged_account_review");
		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(this.items.findAll()).extracting(AccountReviewItem::getUsername)
			.containsExactlyInAnyOrder("rachel", "alice", "carol");
		assertThat(this.items.findAll()).extracting(AccountReviewItem::getOutcome)
			.containsOnly(AccountReviewOutcome.PENDING);
		assertThat(this.service.response(task).active().counts().pending()).isEqualTo(2);
		assertThat(this.service.response(task).suspended().counts().pending()).isEqualTo(1);
		assertThat(itemOf(task, "carol").getCategory()).isEqualTo(AccountReviewCategory.SUSPENDED);
		assertThat(auditEvents()).extracting(AuditRecord::action).contains("create_review_task");
	}

	@Test
	void aNonPrivilegedTaskTakesTheAccountsThatAreNotPrivilegedAndAPrivilegedOneTakesTheOthers() {
		user("rachel");
		plainUser("alice");
		authenticateAsReviewer("ravi");

		Task privileged = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		Task nonPrivileged = this.service.createTask(Task.NON_PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		assertThat(nonPrivileged.getType()).isEqualTo("non_privileged_account_review");
		assertThat(this.items.findAll().stream().filter(item -> item.getTaskId().equals(privileged.getId())))
			.extracting(AccountReviewItem::getUsername)
			.containsExactly("rachel");
		assertThat(itemOf(privileged, "rachel").getPrivilegedPermissions()).containsExactly("user:create");
		assertThat(this.items.findAll().stream().filter(item -> item.getTaskId().equals(nonPrivileged.getId())))
			.extracting(AccountReviewItem::getUsername)
			.containsExactly("alice");
		assertThat(itemOf(nonPrivileged, "alice").getPrivilegedPermissions()).isNull();
		assertThat(this.service.response(nonPrivileged).removed().confirmed()).isFalse();
	}

	@Test
	void aNonPrivilegedTaskNeedsTheRemovedPopulationConfirmedToComplete() {
		plainUser("alice");
		authenticateAsReviewer("ravi");
		Task task = this.service.createTask(Task.NON_PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();
		assertThat(this.service.response(reload(task)).status()).isEqualTo("open");

		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();

		assertThat(this.service.response(reload(task)).status()).isEqualTo("completed");
		assertThat(this.storedReports.existsByTaskId(task.getId())).isTrue();
	}

	@Test
	void eachReviewListsTheSuspendedAndRemovedAccountsOfItsOwnClass() {
		AppUser rachel = user("rachel");
		AppUser alice = plainUser("alice");
		AppUser gone = user("gone");
		AppUser plainGone = plainUser("plain-gone");
		authenticateAs("admin");
		this.lifecycle.suspend(rachel.getPublicId(), ReasonCode.OTHER, null);
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.OTHER, null);
		this.lifecycle.remove(gone.getPublicId(), ReasonCode.OTHER, null);
		this.lifecycle.remove(plainGone.getPublicId(), ReasonCode.OTHER, null);
		Task privileged = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		Task nonPrivileged = this.service.createTask(Task.NON_PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		assertThat(suspendedNames(privileged)).containsExactly("rachel");
		assertThat(suspendedNames(nonPrivileged)).containsExactly("alice");
		assertThat(names(population(privileged, ReviewPopulation.REMOVED))).containsExactly("gone");
		assertThat(names(population(nonPrivileged, ReviewPopulation.REMOVED))).containsExactly("plain-gone");
	}

	@Test
	void suspendedAccountsGetItemsThatShowTheSuspensionAndAreDecidedLikeActiveOnes() {
		user("rachel");
		AppUser alice = user("alice", "Finance");
		AppUser bob = user("bob");
		AppUser carol = user("carol");
		AppRole viewers = this.entityManager.persist(new AppRole("viewers"));
		carol.getRoles().add(viewers);
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.LEFT_ORGANISATION, "resigned");
		this.lifecycle.suspend(carol.getPublicId(), ReasonCode.OTHER, null);
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		this.lifecycle.suspend(bob.getPublicId(), ReasonCode.INACTIVE_ACCOUNT, null);
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		List<ReviewItemResponse> suspended = suspendedRows(task);
		assertThat(suspended).extracting(ReviewItemResponse::username).containsExactly("alice", "bob", "carol");
		ReviewItemResponse row = suspended.get(0);
		assertThat(row.department()).isEqualTo("Finance");
		assertThat(row.createdAt()).isNotNull();
		assertThat(row.suspension().by()).isEqualTo("admin");
		assertThat(row.suspension().reasonCode()).isEqualTo("left_organisation");
		assertThat(row.suspension().note()).isEqualTo("resigned");
		assertThat(row.suspension().at()).isEqualTo(NOW);
		assertThat(suspended.get(1).suspension().by()).isEqualTo("system");
		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(rows(task).get(0).suspension()).isNull();

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "bob").getPublicId()), Decision.REMOVE,
				ReasonCode.NO_LONGER_REQUIRED, null);
		this.service.editRoles(task.getPublicId(), itemOf(task, "carol").getPublicId(), Set.of(viewers.getPublicId()));
		flushAndClear();

		assertThat(this.service.response(task).suspended().counts().confirmed()).isEqualTo(1);
		assertThat(this.service.response(task).suspended().counts().removed()).isEqualTo(1);
		assertThat(this.service.response(task).suspended().counts().confirmedRolesEdited()).isEqualTo(1);
		assertThat(this.service.response(task).suspended().progress().reviewed()).isEqualTo(2);
		assertThat(this.service.response(task).active().counts().confirmed()).isZero();
		assertThat(suspendedRows(task)).extracting(ReviewItemResponse::username).containsExactly("alice", "carol");
		assertThat(suspendedRows(task).get(0).suspension().by()).isEqualTo("admin");
		assertThat(this.service.response(task).status()).isEqualTo("open");
	}

	@Test
	void aTaskWaitsForPendingSuspendedItemsAndCompletesWhenTheyAreDecided() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.OTHER, null);
		authenticateAsReviewer("ravi");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "rachel").getPublicId()), Decision.CONFIRM, null,
				null);
		this.service.confirmPopulation(task.getPublicId(), ReviewPopulation.REMOVED, null);
		flushAndClear();
		assertThat(this.service.response(reload(task)).status()).isEqualTo("open");

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		assertThat(this.service.response(reload(task)).status()).isEqualTo("completed");
	}

	@Test
	void aSuspendedPendingItemLeavesItsListWhenTheAccountIsUnsuspendedAndStopsBlockingCompletion() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.OTHER, null);
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.unsuspend(alice.getPublicId());
		flushAndClear();

		assertThat(suspendedRows(task)).isEmpty();
		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(this.service.response(task).suspended().progress().total()).isZero();
		authenticateAsReviewer("rachel");
		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null, null));
	}

	private List<ReviewItemResponse> suspendedRows(Task task) {
		return page(task, new ItemQuery(AccountReviewCategory.SUSPENDED, null, null, null, null), Sort.by("username"));
	}

	private List<String> suspendedNames(Task task) {
		return suspendedRows(task).stream().map(ReviewItemResponse::username).toList();
	}

	private List<String> names(List<ReviewDtos.PopulationEntryResponse> rows) {
		return rows.stream().map(ReviewDtos.PopulationEntryResponse::username).toList();
	}

	private List<ReviewDtos.PopulationEntryResponse> population(Task task, ReviewPopulation population) {
		return this.service
			.population(task.getPublicId(), population, new AccountReviewService.PopulationQuery(null, null),
					PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
	}

	@Test
	void aTaskWithNoAccountsStaysOpenUntilTheRemovedPopulationIsConfirmed() {
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
	}

	@Test
	void showsPendingRowsLiveNotWhatWasTrueWhenTheTaskWasCreated() {
		user("rachel");
		AppUser alice = user("alice", "Finance");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
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
		assertThat(row.roles()).containsExactly("users");
		assertThat(row.outcome()).isEqualTo("pending");
		assertThat(row.rolesBefore()).isNull();
		assertThat(row.ownAccount()).isFalse();
		assertThat(rows(task).stream().filter(ReviewItemResponse::ownAccount)).extracting(ReviewItemResponse::username)
			.containsExactly("rachel");
	}

	@Test
	void confirmingFreezesTheEvidenceAndIsAudited() {
		user("rachel");
		AppUser alice = user("alice", "Finance");
		alice.getRoles().add(this.entityManager.persist(new AppRole("viewers")));
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.CONFIRMED);
		assertThat(item.getDecidedBy()).isEqualTo("rachel");
		assertThat(item.getDecidedAt()).isEqualTo(NOW);
		assertThat(item.getDepartment()).isEqualTo("Finance");
		assertThat(item.getRolesBefore()).containsExactly("users", "viewers");
		assertThat(item.getRolesAfter()).containsExactly("users", "viewers");
		assertThat(auditEvents()).extracting(AuditRecord::action).contains("confirm_review_item");
		ReviewItemResponse row = rows(task).stream()
			.filter(candidate -> candidate.username().equals("alice"))
			.findFirst()
			.orElseThrow();
		assertThat(row.remark()).isEqualTo("No changes");
		assertThat(this.service.response(task).active().progress().reviewed()).isEqualTo(1);
		assertThat(this.service.response(task).active().counts().confirmed()).isEqualTo(1);
	}

	@Test
	void removingThroughTheReviewMarksTheItemRemovedAndKeepsTheEvidence() {
		user("rachel");
		AppUser alice = user("alice", "HR");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.REMOVE,
				ReasonCode.LEFT_ORGANISATION, "resigned");
		flushAndClear();

		assertThat(this.users.findByPublicId(alice.getPublicId())).isEmpty();
		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.REMOVED);
		assertThat(item.getDecidedBy()).isEqualTo("rachel");
		assertThat(item.getDepartment()).isEqualTo("HR");
		assertThat(item.getRolesBefore()).containsExactly("users");
		assertThat(item.getRolesAfter()).isNull();
		AuditRecord removal = this.trail.find(AuditQuery.where().ids(List.of(item.getRemovalAuditEventId()))).get(0);
		assertThat(removal.action()).isEqualTo("delete_user");
		assertThat(removal.reasonCode()).isEqualTo("left_organisation");
		assertThat(removal.reasonNote()).isEqualTo("resigned");
		assertThat(removal.details(Map.class)).containsEntry("department", "HR");
		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(this.service.response(task).active().counts().removed()).isEqualTo(1);
	}

	@Test
	void editingRolesConfirmsTheItemAndRecordsTheRolesBeforeAndAfter() {
		user("rachel");
		AppUser alice = user("alice");
		AppRole viewers = this.entityManager.persist(new AppRole("viewers"));
		alice.getRoles().add(viewers);
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		this.service.editRoles(task.getPublicId(), itemOf(task, "alice").getPublicId(), Set.of(viewers.getPublicId()));
		flushAndClear();

		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.CONFIRMED_ROLES_EDITED);
		assertThat(item.getRolesBefore()).containsExactly("users", "viewers");
		assertThat(item.getRolesAfter()).containsExactly("viewers");
		AppUser reloaded = this.users.findByPublicId(alice.getPublicId()).orElseThrow();
		assertThat(reloaded.getRoles()).extracting(AppRole::getName).containsExactly("viewers");
		assertThat(auditEvents()).extracting(AuditRecord::action).contains("update_user", "edit_review_item_roles");
		ReviewItemResponse row = rows(task).stream()
			.filter(candidate -> candidate.username().equals("alice"))
			.findFirst()
			.orElseThrow();
		assertThat(row.remark()).isEqualTo("Removed users");
		assertThat(this.service.response(task).active().counts().confirmedRolesEdited()).isEqualTo(1);
	}

	@Test
	void editingRolesRejectsAnUnchangedSetAndAnyRoleTheAccountDoesNotHold() {
		user("rachel");
		user("alice");
		AppRole admins = new AppRole("admins");
		this.entityManager.persist(admins);
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		UUID aliceItem = itemOf(task, "alice").getPublicId();

		assertThatExceptionOfType(BadRequestException.class)
			.isThrownBy(() -> this.service.editRoles(task.getPublicId(), aliceItem, Set.of(this.role.getPublicId())));
		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service
			.editRoles(task.getPublicId(), aliceItem, Set.of(this.role.getPublicId(), admins.getPublicId())));
		assertThatExceptionOfType(com.example.commons.web.problem.ResourceNotFoundException.class)
			.isThrownBy(() -> this.service.editRoles(task.getPublicId(), aliceItem, Set.of(UNKNOWN)));
		assertThat(itemOf(task, "alice").isPending()).isTrue();
	}

	@Test
	void aReviewerCannotActOnTheirOwnAccount() {
		user("rachel");
		user("alice");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		UUID own = itemOf(task, "rachel").getPublicId();

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.decide(task.getPublicId(), List.of(own), Decision.CONFIRM, null, null));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.editRoles(task.getPublicId(), own, Set.of(this.role.getPublicId())));
		assertThat(itemOf(task, "rachel").isPending()).isTrue();
		assertThat(this.trail.find(AuditQuery.where().anyOutcome().targetIds(List.of(own.toString()))))
			.extracting(AuditRecord::action, AuditRecord::outcome, AuditRecord::reasonCode)
			.containsExactly(tuple("confirm_review_item", AuditOutcome.FAILURE, "own_account"),
					tuple("edit_review_item_roles", AuditOutcome.FAILURE, "own_account"));
	}

	@Test
	void aBatchIsAppliedEntirelyOrNotAtAll() {
		user("rachel");
		user("alice");
		user("bob");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
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
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.remove(alice.getPublicId(), ReasonCode.NO_LONGER_REQUIRED, null);
		flushAndClear();

		AccountReviewItem item = itemOf(task, "alice");
		assertThat(item.getOutcome()).isEqualTo(AccountReviewOutcome.REMOVED);
		assertThat(item.getDecidedBy()).isEqualTo("admin");
		assertThat(item.getDepartment()).isEqualTo("IT");
		assertThat(item.getRemovalAuditEventId()).isNotNull();
		assertThat(this.service.response(task).active().progress().total()).isEqualTo(1);
	}

	@Test
	void aSuspendedPendingAccountLeavesTheActiveListAndReturnsWhenUnsuspended() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();

		authenticateAs("admin", Permissions.USER_REMOVE);
		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.POLICY_VIOLATION, null);
		flushAndClear();

		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(this.service.response(task).active().progress().total()).isEqualTo(1);
		assertThat(itemOf(task, "alice").isPending()).isTrue();

		this.lifecycle.unsuspend(alice.getPublicId());
		flushAndClear();

		assertThat(rows(task)).extracting(ReviewItemResponse::username).containsExactly("alice", "rachel");
	}

	@Test
	void aDecidedItemStaysInTheActiveListWhenItsAccountIsLaterSuspended() {
		user("rachel");
		AppUser alice = user("alice");
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		authenticateAs("admin", Permissions.USER_REMOVE);
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
		authenticateAsReviewer("rachel");
		Task task = this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		flushAndClear();
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "bob").getPublicId()), Decision.CONFIRM, null,
				null);
		flushAndClear();

		assertThat(page(task, new ItemQuery(AccountReviewCategory.ACTIVE, null, "finance", null, null),
				Sort.by("username").descending()))
			.extracting(ReviewItemResponse::username)
			.containsExactly("bob", "alice");
		assertThat(page(task,
				new ItemQuery(AccountReviewCategory.ACTIVE, AccountReviewOutcome.CONFIRMED, null, null, null),
				Sort.by("username")))
			.extracting(ReviewItemResponse::username)
			.containsExactly("bob");
		assertThat(page(task, new ItemQuery(AccountReviewCategory.ACTIVE, null, null, "USERS", "arol"),
				Sort.by("username")))
			.extracting(ReviewItemResponse::username)
			.containsExactly("carol");
		assertThat(page(task, ALL, Sort.by("department", "username"))).extracting(ReviewItemResponse::username)
			.containsExactly("rachel", "alice", "bob", "carol");
		assertThat(this.service.departments(task.getPublicId())).containsExactly("Compliance", "Finance", "HR");
	}

	@Test
	void summarisesOpenAndOverdueTasks() {
		user("alice");
		this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW, OCTOBER);
		this.service.createTask(Task.PRIVILEGED_ACCOUNT_REVIEW,
				new ReviewPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
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
