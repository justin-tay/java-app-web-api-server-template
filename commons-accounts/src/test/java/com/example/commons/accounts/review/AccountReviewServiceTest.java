package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewItem;
import com.example.commons.accounts.domain.ReviewItemRepository;
import com.example.commons.accounts.domain.ReviewStatus;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.AccountReviewService.Category;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.ConflictException;

/**
 * Tests {@link AccountReviewService} and {@link ReviewItems} against the real schema: the
 * scope of a task, the live categories, decisions all or none, the rule that a reviewer
 * cannot review their own account, and a task completing when its last item is decided.
 */
@AccountsJpaTest
class AccountReviewServiceTest {

	private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-00000000dead");

	private static final Instant NOW = Instant.parse("2026-05-15T10:00:00Z");

	private static final ReviewWindow WINDOW = new ReviewWindow(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private ReviewItemRepository items;

	@Autowired
	private AccountAuditEventRepository auditEvents;

	private final SessionRevocationService sessionRevocationService = mock(SessionRevocationService.class);

	private AccountReviewService service;

	private AccountLifecycleService lifecycle;

	private AppGroup group;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccountAuditLogger auditLogger = new AccountAuditLogger(this.auditEvents, clock);
		ReviewItems reviewItems = new ReviewItems(this.items, this.tasks, clock);
		this.lifecycle = new AccountLifecycleService(this.users, this.sessionRevocationService, auditLogger, null,
				reviewItems, clock);
		this.service = new AccountReviewService(this.tasks, this.items, this.users, this.auditEvents, this.lifecycle,
				reviewItems, auditLogger, clock, ZoneOffset.UTC);
		this.group = this.entityManager.persist(new AppGroup("users"));
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createsATaskForTheWindowWithAnItemForEveryAccount() {
		user("rachel");
		user("alice");
		AppUser carol = user("carol");
		carol.suspend(NOW, ReasonCode.OTHER, null);

		Task task = this.service.createTask(WINDOW);

		assertThat(task.getType()).isEqualTo("account_review");
		assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
		assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 30));
		assertThat(this.items.findAll()).extracting(ReviewItem::getUsername)
			.containsExactlyInAnyOrder("rachel", "alice", "carol");
		assertThat(this.items.findAll()).extracting(ReviewItem::getReviewStatus)
			.containsOnly(ReviewStatus.PENDING_VERIFICATION);
		assertThat(this.service.response(task).counts()).containsEntry("pending_verification", 3L)
			.containsEntry("verified", 0L);
		assertThat(this.auditEvents.findAll(org.springframework.data.jpa.domain.Specification.unrestricted()))
			.extracting(AccountAuditEvent::getAction)
			.contains("create_review_task");
	}

	@Test
	void showsLiveAccountDataInEachCategoryNotWhatWasTrueWhenTheTaskWasCreated() {
		user("rachel");
		AppUser alice = user("alice");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();

		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.POLICY_VIOLATION, "see ticket");
		flushAndClear();

		assertThat(rows(task, Category.ACTIVE)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		List<ReviewItemResponse> suspended = rows(task, Category.SUSPENDED);
		assertThat(suspended).hasSize(1);
		assertThat(suspended.get(0).username()).isEqualTo("alice");
		assertThat(suspended.get(0).suspendedAt()).isEqualTo(NOW);
		assertThat(suspended.get(0).reasonCode()).isEqualTo("policy_violation");
		assertThat(suspended.get(0).reasonNote()).isEqualTo("see ticket");
		assertThat(suspended.get(0).reviewStatus()).isEqualTo("pending_verification");
	}

	@Test
	void verifiesItemsAndCompletesTheTaskWhenTheLastIsDecided() {
		AppUser rachel = user("rachel");
		AppUser ravi = user("ravi");
		AppUser alice = user("alice");
		this.users.recordLogin("alice", Instant.parse("2026-05-01T00:00:00Z"));
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");

		this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId(), itemOf(task, "ravi").getPublicId()), true, null, null);
		flushAndClear();

		ReviewItem verified = itemOf(task, "alice");
		assertThat(verified.getReviewStatus()).isEqualTo(ReviewStatus.VERIFIED);
		assertThat(verified.getDecidedBy()).isEqualTo("rachel");
		assertThat(verified.getDecidedAt()).isEqualTo(NOW);
		assertThat(verified.getDecidedAccountStatus()).isEqualTo("active");
		assertThat(verified.getDecidedLastLoginAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
		assertThat(this.tasks.findById(task.getId()).orElseThrow().isOpen()).isTrue();

		authenticateAs("ravi");
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "rachel").getPublicId()), true, null, null);
		flushAndClear();

		Task completed = this.tasks.findById(task.getId()).orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(completed.getCompletedBy()).isEqualTo("ravi");
		assertThat(completed.getCompletedAt()).isEqualTo(NOW);
		assertThat(rachel).isNotNull();
		assertThat(ravi).isNotNull();
		assertThat(alice).isNotNull();
	}

	@Test
	void aReviewerCannotReviewTheirOwnAccountAndNothingInTheBatchChanges() {
		user("rachel");
		user("alice");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");

		assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId(), itemOf(task, "rachel").getPublicId()), true, null, null));
		flushAndClear();

		assertThat(itemOf(task, "alice").getReviewStatus()).isEqualTo(ReviewStatus.PENDING_VERIFICATION);
		assertThat(itemOf(task, "rachel").getReviewStatus()).isEqualTo(ReviewStatus.PENDING_VERIFICATION);
		assertThat(rows(task, Category.ACTIVE)).filteredOn(ReviewItemResponse::ownAccount)
			.extracting(ReviewItemResponse::username)
			.containsExactly("rachel");
	}

	@Test
	void aReviewerCannotSuspendRemoveOrUnsuspendTheirOwnAccountFromTheirItem() {
		user("rachel");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");
		UUID itemId = itemOf(task, "rachel").getPublicId();

		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.suspend(task.getPublicId(), itemId, ReasonCode.OTHER, null));
		assertThatExceptionOfType(AccessDeniedException.class)
			.isThrownBy(() -> this.service.decide(task.getPublicId(), List.of(itemId), false, ReasonCode.OTHER, null));

		assertThat(this.users.existsByUsername("rachel")).isTrue();
	}

	@Test
	void rejectsABatchWithAnItemAlreadyDecidedOrFromAnotherTask() {
		user("rachel");
		user("alice");
		user("bob");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), true, null, null);
		flushAndClear();
		UUID bob = itemOf(task, "bob").getPublicId();
		UUID alice = itemOf(task, "alice").getPublicId();

		assertThatExceptionOfType(ConflictException.class)
			.isThrownBy(() -> this.service.decide(task.getPublicId(), List.of(bob, alice, UNKNOWN), true, null, null))
			.withMessageContaining(alice.toString())
			.withMessageContaining(UNKNOWN.toString());
		flushAndClear();

		assertThat(itemOf(task, "bob").getReviewStatus()).isEqualTo(ReviewStatus.PENDING_VERIFICATION);
	}

	@Test
	void removingAnItemDeletesTheAccountFreezesTheItemAndListsItAsRemoved() {
		user("rachel");
		AppUser alice = user("alice");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");

		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), false,
				ReasonCode.LEFT_ORGANISATION, "moved teams");
		flushAndClear();

		assertThat(this.users.existsByUsername("alice")).isFalse();
		ReviewItem item = itemOf(task, "alice");
		assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.REMOVED);
		assertThat(item.getDecidedBy()).isEqualTo("rachel");
		assertThat(item.getDecidedReasonCode()).isEqualTo("left_organisation");
		assertThat(item.getUser()).isNull();
		List<ReviewItemResponse> removed = rows(task, Category.REMOVED);
		assertThat(removed).hasSize(1);
		assertThat(removed.get(0).username()).isEqualTo("alice");
		assertThat(removed.get(0).name()).isEqualTo("alice");
		assertThat(removed.get(0).removedAt()).isEqualTo(NOW);
		assertThat(removed.get(0).removedBy()).isEqualTo("rachel");
		assertThat(removed.get(0).reasonCode()).isEqualTo("left_organisation");
		assertThat(rows(task, Category.ACTIVE)).extracting(ReviewItemResponse::username).containsExactly("rachel");
		assertThat(alice.getPublicId()).isNotNull();
	}

	@Test
	void aRemovalBySomeoneElseOrTheSystemMarksTheOpenItemRemovedAndCanCompleteTheTask() {
		AppUser alice = user("alice");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();

		this.lifecycle.remove(alice.getPublicId(), ReasonCode.INACTIVE_ACCOUNT, null);
		flushAndClear();

		ReviewItem item = itemOf(task, "alice");
		assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.REMOVED);
		assertThat(item.getDecidedBy()).isEqualTo("system");
		assertThat(item.getDecidedReasonCode()).isEqualTo("inactive_account");
		Task completed = this.tasks.findById(task.getId()).orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(completed.getCompletedBy()).isEqualTo("system");
	}

	@Test
	void theRemovedCategoryAlsoListsAnAccountCreatedAndRemovedDuringTheWindow() {
		user("rachel");
		Task task = this.service.createTask(WINDOW);
		AppUser late = user("late-joiner");
		flushAndClear();

		this.lifecycle.remove(late.getPublicId(), ReasonCode.NO_LONGER_REQUIRED, null);
		flushAndClear();

		assertThat(rows(task, Category.REMOVED)).extracting(ReviewItemResponse::username)
			.containsExactly("late-joiner");
		assertThat(this.items.findAll()).extracting(ReviewItem::getUsername).containsExactly("rachel");
	}

	@Test
	void suspendingAndUnsuspendingFromAnItemLeavesItsReviewStatusAlone() {
		user("rachel");
		user("alice");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");
		UUID itemId = itemOf(task, "alice").getPublicId();

		this.service.suspend(task.getPublicId(), itemId, ReasonCode.POLICY_VIOLATION, null);
		flushAndClear();
		assertThat(rows(task, Category.SUSPENDED)).extracting(ReviewItemResponse::username).containsExactly("alice");
		assertThat(itemOf(task, "alice").getReviewStatus()).isEqualTo(ReviewStatus.PENDING_VERIFICATION);

		this.service.unsuspend(task.getPublicId(), itemId);
		flushAndClear();
		assertThat(rows(task, Category.ACTIVE)).extracting(ReviewItemResponse::username)
			.containsExactlyInAnyOrder("rachel", "alice");
		assertThat(itemOf(task, "alice").getReviewStatus()).isEqualTo(ReviewStatus.PENDING_VERIFICATION);
	}

	@Test
	void showsTheLiveSuspensionEvenWhenTheItemWasLoadedBeforeIt() {
		user("rachel");
		AppUser alice = user("alice");
		Task task = this.service.createTask(WINDOW);
		this.entityManager.flush();
		itemOf(task, "alice");

		this.lifecycle.suspend(alice.getPublicId(), ReasonCode.POLICY_VIOLATION, "see ticket");

		List<ReviewItemResponse> suspended = rows(task, Category.SUSPENDED);
		assertThat(suspended).hasSize(1);
		assertThat(suspended.get(0).suspendedAt()).isEqualTo(NOW);
		assertThat(suspended.get(0).reasonCode()).isEqualTo("policy_violation");
	}

	@Test
	void aCompletedTaskRejectsFurtherDecisionsButStillShowsItsRows() {
		AppUser alice = user("alice");
		Task task = this.service.createTask(WINDOW);
		flushAndClear();
		authenticateAs("rachel");
		this.service.decide(task.getPublicId(), List.of(itemOf(task, "alice").getPublicId()), true, null, null);
		flushAndClear();

		assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> this.service.decide(task.getPublicId(),
				List.of(itemOf(task, "alice").getPublicId()), true, null, null));
		assertThat(rows(task, Category.ACTIVE)).extracting(ReviewItemResponse::username).containsExactly("alice");
		assertThat(this.service.summary().openCount()).isZero();
		assertThat(alice).isNotNull();
	}

	@Test
	void summarisesOpenAndOverdueTasks() {
		user("alice");
		this.service.createTask(WINDOW);
		this.service.createTask(new ReviewWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
		flushAndClear();

		assertThat(this.service.summary().openCount()).isEqualTo(2);
		assertThat(this.service.summary().earliestDueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(this.service.summary().overdueCount()).isEqualTo(1);
	}

	@Test
	void aTaskForAnEmptyUserBaseIsCompleteAtOnce() {
		Task task = this.service.createTask(WINDOW);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(task.getCompletedBy()).isEqualTo("system");
	}

	private AppUser user(String username) {
		AppUser user = new AppUser(username, username, null);
		user.getGroups().add(this.group);
		return this.entityManager.persist(user);
	}

	private void flushAndClear() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private ReviewItem itemOf(Task task, String username) {
		return this.items.findAll()
			.stream()
			.filter(item -> item.getTaskId().equals(task.getId()) && item.getUsername().equals(username))
			.findFirst()
			.orElseThrow();
	}

	private List<ReviewItemResponse> rows(Task task, Category category) {
		return this.service.items(task.getPublicId(), category, null, null, PageRequest.of(0, 50, Sort.by("username")))
			.getContent();
	}

	private static void authenticateAs(String username) {
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(username, null, "ROLE_ACCOUNT_REVIEWER"));
	}

}
