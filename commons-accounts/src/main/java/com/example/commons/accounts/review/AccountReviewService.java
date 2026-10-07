package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.admin.Actor;
import com.example.commons.accounts.admin.AdminDtos.Summary;
import com.example.commons.accounts.audit.AccountAudit;
import com.example.commons.accounts.audit.AccountAudit.UserState;
import com.example.commons.accounts.domain.AccountReviewCategory;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AccountReviewOutcome;
import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.AccountReviewReports.Download;
import com.example.commons.accounts.review.AccountReviewReports.Format;
import com.example.commons.accounts.review.ReviewDtos.CategoryStatus;
import com.example.commons.accounts.review.ReviewDtos.Counts;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewDtos.Progress;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskSummary;
import com.example.commons.audit.AuditAction;
import com.example.commons.audit.AuditTrail;
import com.example.commons.audit.Auditor;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.BadRequestException;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Creates account review tasks and lets reviewers work through them: confirming, removing
 * roles from or removing the active and the suspended accounts, and confirming the
 * removed population, which covers the accounts of the review's class. A task completes
 * by itself when the work is done, and storing its report is part of that (see
 * docs/adr/0037, docs/adr/0038 and docs/adr/0039).
 *
 * <p>
 * The scope of a task is fixed when it is created. A pending item shows the live account
 * for as long as the account keeps the status it had then; a decision freezes what the
 * reviewer saw, and a confirmed population freezes its list. A reviewer cannot act on
 * their own account, and a batch is applied entirely or not at all.
 *
 * <p>
 * A reviewer removes access and never grants it. Removing an account needs
 * {@code user:remove}, and removing a role {@code user:remove-role}, as well as
 * {@code review:decide}; adding a role is refused.
 */
@Transactional
public class AccountReviewService {

	public enum Decision {

		CONFIRM, REMOVE

	}

	/**
	 * Criteria for the accounts of one category of a task. Every non-null value narrows
	 * the list.
	 *
	 * @param category the category of accounts to list
	 * @param outcome one outcome
	 * @param department the exact department
	 * @param role the exact role name
	 * @param search matches a username or name containing it
	 */
	public record ItemQuery(AccountReviewCategory category, AccountReviewOutcome outcome, String department,
			String role, String search) {
	}

	/**
	 * Criteria for a population.
	 *
	 * @param department the exact department
	 * @param search matches a username or name containing it
	 */
	public record PopulationQuery(String department, String search) {
	}

	private static final Map<String, Comparator<ReviewItemResponse>> ITEM_ORDER = Map.of("username",
			ListPaging.text(ReviewItemResponse::username), "name", ListPaging.text(ReviewItemResponse::name),
			"department", ListPaging.text(ReviewItemResponse::department), "lastLoginAt",
			ListPaging.time(ReviewItemResponse::lastLoginAt), "decidedAt",
			ListPaging.time(ReviewItemResponse::decidedAt));

	private static final Map<String, Comparator<PopulationEntryResponse>> POPULATION_ORDER = Map.of("username",
			ListPaging.text(PopulationEntryResponse::username), "name", ListPaging.text(PopulationEntryResponse::name),
			"occurredAt", ListPaging.time(PopulationEntryResponse::occurredAt));

	private final TaskRepository tasks;

	private final AccountReviewItemRepository items;

	private final AppUserRepository users;

	private final AppRoleRepository roles;

	private final AccountLifecycleService lifecycle;

	private final SessionRevocationService sessionRevocationService;

	private final AccountAudit audit;

	private final AuditTrail trail;

	private final AccountReviewReports reports;

	private final ReviewPopulations populations;

	private final ReviewReportModels reportModels;

	private final Clock clock;

	private final ZoneId zone;

	public AccountReviewService(TaskRepository tasks, AccountReviewItemRepository items, AppUserRepository users,
			AppRoleRepository roles, AccountLifecycleService lifecycle,
			SessionRevocationService sessionRevocationService, AccountAudit audit, AuditTrail trail,
			AccountReviewReports reports, ReviewPopulations populations, ReviewReportModels reportModels, Clock clock,
			ZoneId zone) {
		this.tasks = tasks;
		this.items = items;
		this.users = users;
		this.roles = roles;
		this.lifecycle = lifecycle;
		this.sessionRevocationService = sessionRevocationService;
		this.audit = audit;
		this.trail = trail;
		this.reports = reports;
		this.populations = populations;
		this.reportModels = reportModels;
		this.clock = clock;
		this.zone = zone;
	}

	/**
	 * Creates the task for a review period, with one item for every active and every
	 * suspended account of its class: the privileged review takes the accounts that are
	 * privileged now and the non-privileged review the others. A review with no such
	 * accounts is complete once the removed population is confirmed.
	 * @param type {@link Task#PRIVILEGED_ACCOUNT_REVIEW} or
	 * {@link Task#NON_PRIVILEGED_ACCOUNT_REVIEW}
	 * @param period the review period
	 * @return the task
	 */
	public Task createTask(String type, ReviewPeriod period) {
		if (!Task.ACCOUNT_REVIEWS.contains(type)) {
			throw new IllegalArgumentException("Not an account review type: " + type);
		}
		Task task = this.tasks.saveAndFlush(new Task(type, period.start(), period.due(), this.clock.instant()));
		boolean privilegedReview = task.isPrivilegedReview();
		List<AppUser> accounts = Stream.of(AccountReviewCategory.values())
			.flatMap(category -> this.users.findByStatus(category.status()).stream())
			.filter(user -> user.isPrivileged() == privilegedReview)
			.toList();
		Map<UUID, String> suspenders = this.audit.lastSuspenders(accounts.stream()
			.filter(user -> user.getStatus() == AccountStatus.SUSPENDED)
			.map(AppUser::getPublicId)
			.toList());
		List<AccountReviewItem> created = accounts.stream()
			.map(user -> new AccountReviewItem(task.getId(), user,
					privilegedReview ? user.privilegedPermissionNames() : null, suspenders.get(user.getPublicId())))
			.toList();
		this.items.saveAll(created);
		this.trail.record(ReviewAudit.task(ReviewAudit.CREATE_REVIEW_TASK, task, type + " " + period.start())
			.details(new ReviewAudit.TaskCreated(type, period.start().toString(), period.due().toString(),
					created.size()))
			.build());
		return task;
	}

	@Transactional(readOnly = true)
	public Page<Task> tasks(String type, TaskStatus status, Pageable pageable) {
		Specification<Task> specification = Specification.unrestricted();
		if (type != null) {
			specification = specification.and((root, query, builder) -> builder.equal(root.get("type"), type));
		}
		if (status != null) {
			specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
		}
		return this.tasks.findAll(specification, pageable);
	}

	@Transactional(readOnly = true)
	public Task task(UUID id) {
		return this.tasks.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("Task"));
	}

	@Transactional(readOnly = true)
	public TaskSummary summary() {
		LocalDate today = today();
		return new TaskSummary(this.tasks.countByStatus(TaskStatus.OPEN),
				this.tasks.findFirstByStatusOrderByDueDateAsc(TaskStatus.OPEN).map(Task::getDueDate).orElse(null),
				this.tasks.countByStatusAndDueDateBefore(TaskStatus.OPEN, today));
	}

	@Transactional(readOnly = true)
	public TaskResponse response(Task task) {
		Map<AccountReviewCategory, Map<AccountReviewOutcome, Long>> byCategory = new EnumMap<>(
				AccountReviewCategory.class);
		for (Object[] row : this.items.countByCategoryAndOutcome(task.getId())) {
			byCategory.computeIfAbsent((AccountReviewCategory) row[0], key -> new EnumMap<>(AccountReviewOutcome.class))
				.put((AccountReviewOutcome) row[1], (Long) row[2]);
		}
		boolean overdue = task.isOpen() && task.getDueDate().isBefore(today());
		return new TaskResponse(task.getPublicId(), task.getType(), task.getStatus().value(), task.getStartDate(),
				task.getDueDate(), task.getCompletedAt(), task.getCompletedBy(), overdue,
				categoryStatus(task, AccountReviewCategory.ACTIVE, byCategory),
				categoryStatus(task, AccountReviewCategory.SUSPENDED, byCategory), this.populations.status(task),
				this.reports.exists(task));
	}

	private CategoryStatus categoryStatus(Task task, AccountReviewCategory category,
			Map<AccountReviewCategory, Map<AccountReviewOutcome, Long>> byCategory) {
		Map<AccountReviewOutcome, Long> byOutcome = byCategory.getOrDefault(category, Map.of());
		long confirmed = byOutcome.getOrDefault(AccountReviewOutcome.CONFIRMED, 0L);
		long edited = byOutcome.getOrDefault(AccountReviewOutcome.CONFIRMED_ROLES_EDITED, 0L);
		Counts counts = new Counts(byOutcome.getOrDefault(AccountReviewOutcome.PENDING, 0L), confirmed, edited,
				byOutcome.getOrDefault(AccountReviewOutcome.REMOVED, 0L));
		long decided = confirmed + edited;
		return new CategoryStatus(counts, new Progress(decided, decided + pending(task, category)));
	}

	private long pending(Task task, AccountReviewCategory category) {
		return this.items.countPending(task.getId(), category, category.status());
	}

	/**
	 * Lists the accounts of one category of a task: the pending items whose account still
	 * has the status of the category, and every decided item that was not removed.
	 * Pending rows show the live account, decided rows what was frozen.
	 * @param taskId the task
	 * @param query the filters
	 * @param pageable the page and sort, whose properties are the API's names
	 * @return the page of rows
	 */
	@Transactional(readOnly = true)
	public Page<ReviewItemResponse> items(UUID taskId, ItemQuery query, Pageable pageable) {
		return ListPaging.page(
				itemRows(task(taskId), query.category()).stream().filter(row -> matches(row, query)).toList(), pageable,
				ITEM_ORDER);
	}

	/**
	 * Lists the removed population of a task: live until it is confirmed, then the frozen
	 * list. A review lists the accounts of its own class.
	 */
	@Transactional(readOnly = true)
	public Page<PopulationEntryResponse> population(UUID taskId, ReviewPopulation population, PopulationQuery query,
			Pageable pageable) {
		List<PopulationEntryResponse> rows = this.populations.rows(task(taskId), population)
			.stream()
			.filter(row -> matches(row, query))
			.toList();
		return ListPaging.page(rows, pageable, POPULATION_ORDER);
	}

	/**
	 * Returns the distinct departments shown in a task, for a filter control.
	 */
	@Transactional(readOnly = true)
	public List<String> departments(UUID taskId) {
		Task task = task(taskId);
		Set<String> departments = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (AccountReviewCategory category : AccountReviewCategory.values()) {
			itemRows(task, category).stream()
				.map(ReviewItemResponse::department)
				.filter(java.util.Objects::nonNull)
				.forEach(departments::add);
		}
		for (ReviewPopulation population : ReviewPopulation.values()) {
			this.populations.rows(task, population)
				.stream()
				.map(PopulationEntryResponse::department)
				.filter(java.util.Objects::nonNull)
				.forEach(departments::add);
		}
		return List.copyOf(departments);
	}

	/**
	 * Confirms or removes the given items, all or none. Removing needs a reason, and the
	 * reviewer must hold {@code user:remove}.
	 * @param taskId the task
	 * @param itemIds the items
	 * @param decision confirm or remove
	 * @param reason the reason for a removal
	 * @param note the optional note for a removal
	 */
	public void decide(UUID taskId, List<UUID> itemIds, Decision decision, ReasonCode reason, String note) {
		Task task = openTask(taskId);
		AuditAction action = (decision == Decision.CONFIRM) ? ReviewAudit.CONFIRM_REVIEW_ITEM
				: ReviewAudit.REMOVE_REVIEW_ITEM;
		if (decision == Decision.REMOVE && !Actor.currentHolds(Permissions.USER_REMOVE)) {
			throw this.trail.reject(ReviewAudit.refusal(action, taskId).reason("missing_permission").build(),
					() -> new AccessDeniedException("Removing an account needs " + Permissions.USER_REMOVE + "."));
		}
		Set<UUID> ids = new LinkedHashSet<>(itemIds);
		Map<UUID, AccountReviewItem> found = this.items.findByTaskIdAndPublicIdIn(task.getId(), ids)
			.stream()
			.collect(Collectors.toMap(AccountReviewItem::getPublicId, item -> item));
		String actor = Auditor.current();
		rejectOwnAccount(found.values(), actor, taskId, action);
		List<UUID> rejected = ids.stream().filter(id -> !decidable(found.get(id))).toList();
		if (!rejected.isEmpty()) {
			throw new ConflictException("These items are not in this task, no longer have the status they were "
					+ "reviewed with or are already decided: "
					+ rejected.stream().map(UUID::toString).collect(Collectors.joining(", ")));
		}
		for (UUID id : ids) {
			AccountReviewItem item = found.get(id);
			AppUser account = item.getUser();
			if (decision == Decision.CONFIRM) {
				item.confirm(account, account.roleNames(), actor, this.clock.instant());
				this.items.save(item);
				this.trail.record(ReviewAudit.item(action, item, taskId).build());
			}
			else {
				// The lifecycle service marks the item removed, with this actor as the
				// decider.
				this.lifecycle.remove(account, reason, note);
				this.trail.record(ReviewAudit.item(action, item, taskId).reason(reason.value(), note).build());
			}
		}
		this.items.flush();
		completeIfFinished(task, actor);
	}

	/**
	 * Sets the roles of an active or suspended account in a task, which confirms it in
	 * the same step. The reviewer can only remove roles: a role the account does not hold
	 * is refused, because adding access is not the reviewer's to do (docs/adr/0038).
	 * Needs {@code user:remove-role}.
	 * @param taskId the task
	 * @param itemId the item
	 * @param roleIds the full set of roles the account should hold
	 */
	public void editRoles(UUID taskId, UUID itemId, Set<UUID> roleIds) {
		Task task = openTask(taskId);
		if (!Actor.currentHolds(Permissions.USER_REMOVE_ROLE)) {
			throw this.trail.reject(
					ReviewAudit.refusal(ReviewAudit.EDIT_REVIEW_ITEM_ROLES, taskId)
						.reason("missing_permission")
						.build(),
					() -> new AccessDeniedException("Removing a role needs " + Permissions.USER_REMOVE_ROLE + "."));
		}
		AccountReviewItem item = this.items.findByPublicId(itemId)
			.filter(candidate -> candidate.getTaskId().equals(task.getId()))
			.orElseThrow(() -> new ResourceNotFoundException("Review item"));
		String actor = Auditor.current();
		rejectOwnAccount(List.of(item), actor, taskId, ReviewAudit.EDIT_REVIEW_ITEM_ROLES);
		if (!decidable(item)) {
			throw new ConflictException(
					"The item no longer has the status it was reviewed with or is already decided.");
		}
		AppUser account = item.getUser();
		Set<AppRole> requested = Set.copyOf(this.roles.findAllByPublicIdIn(roleIds));
		if (requested.size() != roleIds.size()) {
			throw new ResourceNotFoundException("Role");
		}
		List<String> before = account.roleNames();
		UserState state = UserState.of(account);
		if (requested.equals(account.getRoles())) {
			throw new BadRequestException("The roles are unchanged.");
		}
		if (!account.getRoles().containsAll(requested)) {
			throw this.audit.userUpdateRejected(state, "role_added_in_review",
					() -> new AccessDeniedException("A reviewer cannot add a role."));
		}
		account.getRoles().clear();
		account.getRoles().addAll(requested);
		this.sessionRevocationService.revoke(account.getUsername(), "privilege_change");
		this.audit.userUpdated(state, UserState.of(account));
		List<String> after = account.roleNames();
		item.confirmWithRolesEdited(account, before, after, actor, this.clock.instant());
		this.items.saveAndFlush(item);
		this.trail.record(ReviewAudit.item(ReviewAudit.EDIT_REVIEW_ITEM_ROLES, item, taskId)
			.details(
					new ReviewAudit.ItemDecided(taskId, before.stream().filter(name -> !after.contains(name)).toList()))
			.build());
		completeIfFinished(task, actor);
	}

	/**
	 * Confirms the removed population of a task, once. The list as it is now is frozen
	 * and the population becomes read-only.
	 * @param taskId the task
	 * @param population the population
	 * @param note the optional note
	 */
	public void confirmPopulation(UUID taskId, ReviewPopulation population, String note) {
		Task task = openTask(taskId);
		String actor = Auditor.current();
		int count = this.populations.confirm(task, population, actor, note);
		this.trail.record(ReviewAudit.task(ReviewAudit.CONFIRM_REVIEW_POPULATION, task, population.value())
			.reason(null, note)
			.details(new ReviewAudit.PopulationConfirmed(taskId, population.value(), count))
			.build());
		completeIfFinished(task, actor);
	}

	/**
	 * Returns the public IDs of the tasks still open, for the job that completes tasks
	 * finished by a change outside the review.
	 */
	@Transactional(readOnly = true)
	public List<UUID> openTaskIds() {
		return this.tasks.findByTypeInAndStatus(Task.ACCOUNT_REVIEWS, TaskStatus.OPEN)
			.stream()
			.map(Task::getPublicId)
			.toList();
	}

	/**
	 * Completes a task whose work has been finished by a change outside the review, such
	 * as the inactivity job removing the last pending account. The completer is
	 * {@code system}.
	 * @param taskId the task
	 */
	public void completeIfFinished(UUID taskId) {
		completeIfFinished(task(taskId), Auditor.SYSTEM);
	}

	private void completeIfFinished(Task task, String by) {
		if (!task.isOpen()
				|| Stream.of(AccountReviewCategory.values()).anyMatch(category -> pending(task, category) > 0)
				|| !this.populations.allConfirmed(task)) {
			return;
		}
		task.complete(this.clock.instant(), by);
		this.tasks.saveAndFlush(task);
		this.reports.store(task, this.reportModels.build(task, false, by), by);
	}

	/**
	 * Returns a report in a format: the stored PDF for a completed task, otherwise a file
	 * generated now and marked as a draft for an open one. The download is audited.
	 * @param taskId the task
	 * @param format the format
	 */
	public Download download(UUID taskId, Format format) {
		Task task = task(taskId);
		return this.reports.download(task, this.reportModels.build(task, task.isOpen(), Auditor.current()), format);
	}

	private Task openTask(UUID taskId) {
		Task task = task(taskId);
		if (!task.isOpen()) {
			throw new ConflictException("The review task is completed.");
		}
		return task;
	}

	private void rejectOwnAccount(Collection<AccountReviewItem> found, String actor, UUID taskId, AuditAction action) {
		for (AccountReviewItem item : found) {
			if (item.getUsername().equals(actor)) {
				throw this.trail.reject(ReviewAudit.item(action, item, taskId).reason("own_account").build(),
						() -> new AccessDeniedException("Reviewers cannot review their own account."));
			}
		}
	}

	/**
	 * Returns whether the item is pending and its account exists and still has the status
	 * of the item's category.
	 */
	private static boolean decidable(AccountReviewItem item) {
		return item != null && item.isPending() && item.accountInCategory();
	}

	private List<ReviewItemResponse> itemRows(Task task, AccountReviewCategory category) {
		String actor = Auditor.current();
		return this.items.findAllWithAccount(task.getId())
			.stream()
			.filter(item -> item.getCategory() == category && listed(item))
			.map(item -> row(item, actor))
			.toList();
	}

	private static boolean listed(AccountReviewItem item) {
		return switch (item.getOutcome()) {
			case CONFIRMED, CONFIRMED_ROLES_EDITED -> true;
			case PENDING -> item.accountInCategory();
			case REMOVED -> false;
		};
	}

	private static ReviewItemResponse row(AccountReviewItem item, String actor) {
		boolean own = item.getUsername().equals(actor);
		ReviewItemView view = ReviewItemView.of(item, null);
		if (item.isPending()) {
			AppUser user = item.getUser();
			return new ReviewItemResponse(item.getPublicId(), item.getUserPublicId(), item.getUsername(), view.name(),
					view.department(), view.createdAt(), view.roles(), null, item.getPrivilegedPermissions(),
					currentRoles(user), user.getLastLoginAt(), user.lastActivityAt(), view.suspension(),
					item.getOutcome().value(), null, own, null, null);
		}
		return new ReviewItemResponse(item.getPublicId(), item.getUserPublicId(), item.getUsername(), view.name(),
				view.department(), view.createdAt(), view.roles(), item.getRolesBefore(),
				item.getPrivilegedPermissions(), null, item.getLastLoginAt(), item.getLastActivityAt(),
				view.suspension(), item.getOutcome().value(), view.remark(), own, item.getDecidedBy(),
				item.getDecidedAt());
	}

	/**
	 * Returns the roles an account holds now with their IDs, sorted by name, which is
	 * what a client needs to send the roles that remain when it removes some.
	 */
	private static List<Summary> currentRoles(AppUser user) {
		return user.getRoles()
			.stream()
			.map(role -> new Summary(role.getPublicId(), role.getName()))
			.sorted(Comparator.comparing(Summary::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static boolean matches(ReviewItemResponse row, ItemQuery query) {
		return (query.outcome() == null || row.outcome().equals(query.outcome().value()))
				&& (query.department() == null || query.department().equalsIgnoreCase(row.department()))
				&& (query.role() == null || row.roles().stream().anyMatch(name -> name.equalsIgnoreCase(query.role())))
				&& containsText(query.search(), row.username(), row.name());
	}

	private static boolean matches(PopulationEntryResponse row, PopulationQuery query) {
		return (query.department() == null || query.department().equalsIgnoreCase(row.department()))
				&& containsText(query.search(), row.username(), row.name());
	}

	private static boolean containsText(String search, String username, String name) {
		if (search == null || search.isBlank()) {
			return true;
		}
		String needle = search.toLowerCase(Locale.ROOT);
		return username.toLowerCase(Locale.ROOT).contains(needle)
				|| (name != null && name.toLowerCase(Locale.ROOT).contains(needle));
	}

	private LocalDate today() {
		return LocalDate.ofInstant(this.clock.instant(), this.zone);
	}

}
