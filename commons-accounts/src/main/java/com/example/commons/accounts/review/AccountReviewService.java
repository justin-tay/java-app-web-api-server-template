package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountAuditLogger.UserState;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.admin.Actor;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AccountReviewAttestation;
import com.example.commons.accounts.domain.AccountReviewAttestationRepository;
import com.example.commons.accounts.domain.AccountReviewItem;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AccountReviewOutcome;
import com.example.commons.accounts.domain.AccountReviewPopulationEntry;
import com.example.commons.accounts.domain.AccountReviewPopulationEntryRepository;
import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.Auditor;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.AccountReviewReports.Download;
import com.example.commons.accounts.review.AccountReviewReports.Format;
import com.example.commons.accounts.review.ReviewDtos.Counts;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewDtos.PopulationStatus;
import com.example.commons.accounts.review.ReviewDtos.Populations;
import com.example.commons.accounts.review.ReviewDtos.Progress;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskSummary;
import com.example.commons.security.session.SessionRevocationService;
import com.example.commons.web.problem.BadRequestException;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Creates account review tasks and lets reviewers work through them: confirming, removing
 * roles from or removing the active accounts, and, in the privileged account review,
 * confirming the suspended and removed populations. A task completes by itself when the
 * work is done, and storing its report is part of that (see docs/adr/0037 and
 * docs/adr/0038).
 *
 * <p>
 * The scope of a task is fixed when it is created. A pending item shows the live account;
 * a decision freezes what the reviewer saw, and a confirmed population freezes its list.
 * A reviewer cannot act on their own account, and a batch is applied entirely or not at
 * all.
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
	 * Criteria for the active accounts of a task. Every non-null value narrows the list.
	 *
	 * @param outcome one outcome
	 * @param department the exact department
	 * @param role the exact role name
	 * @param search matches a username or name containing it
	 */
	public record ItemQuery(AccountReviewOutcome outcome, String department, String role, String search) {
	}

	/**
	 * Criteria for a population.
	 *
	 * @param department the exact department
	 * @param search matches a username or name containing it
	 */
	public record PopulationQuery(String department, String search) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {
	};

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

	private final AccountReviewAttestationRepository attestations;

	private final AccountReviewPopulationEntryRepository entries;

	private final AppUserRepository users;

	private final AppRoleRepository roles;

	private final AccountAuditEventRepository auditEvents;

	private final AccountLifecycleService lifecycle;

	private final SessionRevocationService sessionRevocationService;

	private final AccountAuditLogger auditLogger;

	private final AccountReviewReports reports;

	private final Clock clock;

	private final ZoneId zone;

	public AccountReviewService(TaskRepository tasks, AccountReviewItemRepository items,
			AccountReviewAttestationRepository attestations, AccountReviewPopulationEntryRepository entries,
			AppUserRepository users, AppRoleRepository roles, AccountAuditEventRepository auditEvents,
			AccountLifecycleService lifecycle, SessionRevocationService sessionRevocationService,
			AccountAuditLogger auditLogger, AccountReviewReports reports, Clock clock, ZoneId zone) {
		this.tasks = tasks;
		this.items = items;
		this.attestations = attestations;
		this.entries = entries;
		this.users = users;
		this.roles = roles;
		this.auditEvents = auditEvents;
		this.lifecycle = lifecycle;
		this.sessionRevocationService = sessionRevocationService;
		this.auditLogger = auditLogger;
		this.reports = reports;
		this.clock = clock;
		this.zone = zone;
	}

	/**
	 * Creates the task for a review period, with one item for every active account of its
	 * class: the privileged review takes the accounts that are privileged now and the
	 * non-privileged review the others. A privileged review with no active accounts is
	 * complete once both populations are confirmed.
	 * @param type {@link Task#PRIVILEGED_ACCOUNT_REVIEW} or
	 * {@link Task#NON_PRIVILEGED_ACCOUNT_REVIEW}
	 * @param period the review period
	 * @return the task
	 */
	public Task createTask(String type, ReviewPeriod period) {
		if (!Task.ACCOUNT_REVIEWS.contains(type)) {
			throw new IllegalArgumentException("Not an account review type: " + type);
		}
		boolean privilegedReview = Task.PRIVILEGED_ACCOUNT_REVIEW.equals(type);
		Task task = this.tasks.saveAndFlush(new Task(type, period.start(), period.due(), this.clock.instant()));
		List<AccountReviewItem> created = this.users.findByStatus(AccountStatus.ACTIVE)
			.stream()
			.filter(user -> user.isPrivileged() == privilegedReview)
			.map(user -> new AccountReviewItem(task.getId(), user,
					privilegedReview ? ReviewItems.privilegedPermissions(user) : null))
			.toList();
		this.items.saveAll(created);
		this.auditLogger.record("create_review_task", "REVIEW", task.getPublicId().toString(),
				type + " " + period.start(), null, null, Map.of("type", type, "startDate", period.start().toString(),
						"dueDate", period.due().toString(), "itemCount", created.size()));
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
		Map<AccountReviewOutcome, Long> byOutcome = new HashMap<>();
		for (Object[] row : this.items.countByOutcome(task.getId())) {
			byOutcome.put((AccountReviewOutcome) row[0], (Long) row[1]);
		}
		long confirmed = byOutcome.getOrDefault(AccountReviewOutcome.CONFIRMED, 0L);
		long edited = byOutcome.getOrDefault(AccountReviewOutcome.CONFIRMED_ROLES_EDITED, 0L);
		Counts counts = new Counts(byOutcome.getOrDefault(AccountReviewOutcome.PENDING, 0L), confirmed, edited,
				byOutcome.getOrDefault(AccountReviewOutcome.REMOVED, 0L));
		long decided = confirmed + edited;
		Progress progress = new Progress(decided, decided + this.items.countPendingWithActiveAccount(task.getId()));
		Map<ReviewPopulation, AccountReviewAttestation> attested = this.attestations.findByTaskId(task.getId())
			.stream()
			.collect(Collectors.toMap(AccountReviewAttestation::getPopulation, attestation -> attestation));
		boolean overdue = task.isOpen() && task.getDueDate().isBefore(today());
		Populations populations = isPrivileged(task) ? new Populations(status(attested.get(ReviewPopulation.SUSPENDED)),
				status(attested.get(ReviewPopulation.REMOVED))) : null;
		return new TaskResponse(task.getPublicId(), task.getType(), task.getStatus().value(), task.getStartDate(),
				task.getDueDate(), task.getCompletedAt(), task.getCompletedBy(), overdue, counts, progress, populations,
				this.reports.exists(task));
	}

	/**
	 * Lists the active accounts of a task: the pending items whose account is active, and
	 * every decided item that was not removed. Pending rows show the live account,
	 * decided rows what was frozen.
	 * @param taskId the task
	 * @param query the filters
	 * @param pageable the page and sort, whose properties are the API's names
	 * @return the page of rows
	 */
	@Transactional(readOnly = true)
	public Page<ReviewItemResponse> items(UUID taskId, ItemQuery query, Pageable pageable) {
		return ListPaging.page(itemRows(task(taskId)).stream().filter(row -> matches(row, query)).toList(), pageable,
				ITEM_ORDER);
	}

	/**
	 * Lists one population of a task: live until it is confirmed, then the frozen list.
	 * Only the privileged account review has populations.
	 */
	@Transactional(readOnly = true)
	public Page<PopulationEntryResponse> population(UUID taskId, ReviewPopulation population, PopulationQuery query,
			Pageable pageable) {
		List<PopulationEntryResponse> rows = populationRows(privilegedTask(taskId), population).stream()
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
		itemRows(task).stream()
			.map(ReviewItemResponse::department)
			.filter(java.util.Objects::nonNull)
			.forEach(departments::add);
		if (isPrivileged(task)) {
			for (ReviewPopulation population : ReviewPopulation.values()) {
				populationRows(task, population).stream()
					.map(PopulationEntryResponse::department)
					.filter(java.util.Objects::nonNull)
					.forEach(departments::add);
			}
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
		if (decision == Decision.REMOVE && !Actor.currentHolds(Permissions.USER_REMOVE)) {
			this.auditLogger.record("review_rejected", "REVIEW", taskId.toString(), "decide", "missing_permission",
					null, Map.of("taskId", taskId));
			throw new AccessDeniedException("Removing an account needs " + Permissions.USER_REMOVE + ".");
		}
		Set<UUID> ids = new LinkedHashSet<>(itemIds);
		Map<UUID, AccountReviewItem> found = this.items.findByTaskIdAndPublicIdIn(task.getId(), ids)
			.stream()
			.collect(Collectors.toMap(AccountReviewItem::getPublicId, item -> item));
		String actor = Auditor.current();
		rejectOwnAccount(found.values(), actor, taskId);
		List<UUID> rejected = ids.stream().filter(id -> !decidable(found.get(id))).toList();
		if (!rejected.isEmpty()) {
			throw new ConflictException("These items are not in this task, are not active accounts or are already "
					+ "decided: " + rejected.stream().map(UUID::toString).collect(Collectors.joining(", ")));
		}
		for (UUID id : ids) {
			AccountReviewItem item = found.get(id);
			AppUser account = item.getUser();
			if (decision == Decision.CONFIRM) {
				item.confirm(account, ReviewItems.roleNames(account), actor, this.clock.instant());
				this.items.save(item);
				this.auditLogger.record("confirm_review_item", "REVIEW", item.getPublicId().toString(),
						item.getUsername(), null, null, Map.of("taskId", taskId));
			}
			else {
				// The lifecycle service marks the item removed, with this actor as the
				// decider.
				this.lifecycle.remove(account, reason, note);
				this.auditLogger.record("remove_review_item", "REVIEW", item.getPublicId().toString(),
						item.getUsername(), reason.value(), note, Map.of("taskId", taskId));
			}
		}
		this.items.flush();
		completeIfFinished(task, actor);
	}

	/**
	 * Sets the roles of an active account in a task, which confirms it in the same step.
	 * The reviewer can only remove roles: a role the account does not hold is refused,
	 * because adding access is not the reviewer's to do (docs/adr/0038). Needs
	 * {@code user:remove-role}.
	 * @param taskId the task
	 * @param itemId the item
	 * @param roleIds the full set of roles the account should hold
	 */
	public void editRoles(UUID taskId, UUID itemId, Set<UUID> roleIds) {
		Task task = openTask(taskId);
		if (!Actor.currentHolds(Permissions.USER_REMOVE_ROLE)) {
			this.auditLogger.record("review_rejected", "REVIEW", itemId.toString(), "edit_roles", "missing_permission",
					null, Map.of("taskId", taskId));
			throw new AccessDeniedException("Removing a role needs " + Permissions.USER_REMOVE_ROLE + ".");
		}
		AccountReviewItem item = this.items.findByPublicId(itemId)
			.filter(candidate -> candidate.getTaskId().equals(task.getId()))
			.orElseThrow(() -> new ResourceNotFoundException("Review item"));
		String actor = Auditor.current();
		rejectOwnAccount(List.of(item), actor, taskId);
		if (!decidable(item)) {
			throw new ConflictException("The item is not an active account or is already decided.");
		}
		AppUser account = item.getUser();
		Set<AppRole> requested = Set.copyOf(this.roles.findAllByPublicIdIn(roleIds));
		if (requested.size() != roleIds.size()) {
			throw new ResourceNotFoundException("Role");
		}
		List<String> before = ReviewItems.roleNames(account);
		UserState state = UserState.of(account);
		if (requested.equals(account.getRoles())) {
			throw new BadRequestException("The roles are unchanged.");
		}
		if (!account.getRoles().containsAll(requested)) {
			this.auditLogger.userUpdateRejected(state, "role_added_in_review");
			throw new AccessDeniedException("A reviewer cannot add a role.");
		}
		account.getRoles().clear();
		account.getRoles().addAll(requested);
		this.sessionRevocationService.revoke(account.getUsername(), "privilege_change");
		this.auditLogger.userUpdated(state, UserState.of(account));
		List<String> after = ReviewItems.roleNames(account);
		item.confirmWithRolesEdited(account, before, after, actor, this.clock.instant());
		this.items.saveAndFlush(item);
		this.auditLogger.record("edit_review_item_roles", "REVIEW", item.getPublicId().toString(), item.getUsername(),
				null, null, Map.of("taskId", taskId, "rolesRemoved",
						before.stream().filter(name -> !after.contains(name)).toList()));
		completeIfFinished(task, actor);
	}

	/**
	 * Confirms the suspended or the removed population of a task, once. The list as it is
	 * now is frozen and the population becomes read-only. Only the privileged account
	 * review has populations.
	 * @param taskId the task
	 * @param population the population
	 * @param note the optional note
	 */
	public void confirmPopulation(UUID taskId, ReviewPopulation population, String note) {
		Task task = openTask(taskId);
		if (!isPrivileged(task)) {
			throw new BadRequestException("Only the privileged account review has populations.");
		}
		if (this.attestations.findByTaskIdAndPopulation(task.getId(), population).isPresent()) {
			throw new ConflictException("The population is already confirmed.");
		}
		String actor = Auditor.current();
		List<PopulationEntryResponse> rows = populationRows(task, population);
		AccountReviewAttestation attestation = this.attestations.save(
				new AccountReviewAttestation(task.getId(), population, actor, this.clock.instant(), note, rows.size()));
		this.entries.saveAll(rows.stream()
			.map(row -> new AccountReviewPopulationEntry(attestation.getId(), row.userId(), row.username(), row.name(),
					row.department(), row.lastLoginAt(), row.occurredAt(), row.actor(), row.reasonCode(),
					row.reasonNote()))
			.toList());
		this.auditLogger.record("confirm_review_population", "REVIEW", task.getPublicId().toString(),
				population.value(), null, note,
				Map.of("taskId", taskId, "population", population.value(), "count", rows.size()));
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
		int populations = isPrivileged(task) ? ReviewPopulation.values().length : 0;
		if (!task.isOpen() || this.items.countPendingWithActiveAccount(task.getId()) > 0
				|| this.attestations.findByTaskId(task.getId()).size() < populations) {
			return;
		}
		task.complete(this.clock.instant(), by);
		this.tasks.saveAndFlush(task);
		this.reports.store(task, reportModel(task, false, by), by);
	}

	/**
	 * Returns a report in a format: the stored PDF for a completed task, otherwise a file
	 * generated now and marked as a draft for an open one. The download is audited.
	 * @param taskId the task
	 * @param format the format
	 */
	public Download download(UUID taskId, Format format) {
		Task task = task(taskId);
		return this.reports.download(task, reportModel(task, task.isOpen(), Auditor.current()), format);
	}

	/**
	 * Assembles the report of a task from its items and populations, which are frozen for
	 * what has been decided and confirmed and live for the rest.
	 */
	private ReviewReportModel reportModel(Task task, boolean draft, String generatedBy) {
		List<AccountReviewItem> all = this.items.findAllWithAccount(task.getId());
		Map<Long, AccountAuditEvent> removals = this.auditEvents.findAllById(
				all.stream().map(AccountReviewItem::getRemovalAuditEventId).filter(java.util.Objects::nonNull).toList())
			.stream()
			.collect(Collectors.toMap(AccountAuditEvent::getId, event -> event));
		List<ReviewReportModel.ItemRow> rows = new ArrayList<>();
		Map<String, long[]> byDepartment = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		long[] total = new long[5];
		List<AccountReviewItem> ordered = all.stream()
			.sorted(Comparator.comparing(item -> displayName(item), String.CASE_INSENSITIVE_ORDER))
			.toList();
		for (AccountReviewItem item : ordered) {
			boolean pending = item.isPending();
			AppUser live = item.getUser();
			String department = pending && live != null ? live.getDepartment() : item.getDepartment();
			List<String> roleNames = pending ? (live == null ? List.of() : ReviewItems.roleNames(live))
					: item.getRolesAfter() == null ? List.of() : item.getRolesAfter();
			AccountAuditEvent removal = item.getRemovalAuditEventId() == null ? null
					: removals.get(item.getRemovalAuditEventId());
			String remark = ReviewRemarks.remark(item.getOutcome(), item.getRolesBefore(), item.getRolesAfter(),
					removal == null ? null : removal.getReasonCode());
			rows.add(new ReviewReportModel.ItemRow(rows.size() + 1, displayName(item), item.getUsername(), department,
					String.join(", ", roleNames), outcomeLabel(item.getOutcome()), remark, item.getDecidedAt()));
			int column = item.getOutcome().ordinal();
			byDepartment.computeIfAbsent(department == null ? "(none)" : department, key -> new long[5])[column]++;
			byDepartment.get(department == null ? "(none)" : department)[4]++;
			total[column]++;
			total[4]++;
		}
		List<ReviewReportModel.DepartmentRow> departments = byDepartment.entrySet()
			.stream()
			.map(entry -> departmentRow(entry.getKey(), entry.getValue()))
			.toList();
		boolean privileged = isPrivileged(task);
		return new ReviewReportModel(draft, privileged, this.zone, task.getStartDate(), task.getDueDate(),
				task.getDueDate(), task.getCompletedAt(), task.getCompletedBy(), this.clock.instant(), generatedBy,
				new ReviewReportModel.Summary(total[AccountReviewOutcome.CONFIRMED.ordinal()],
						total[AccountReviewOutcome.CONFIRMED_ROLES_EDITED.ordinal()],
						total[AccountReviewOutcome.REMOVED.ordinal()], total[AccountReviewOutcome.PENDING.ordinal()],
						total[4]),
				departments, rows, privileged ? section(task, ReviewPopulation.SUSPENDED) : null,
				privileged ? section(task, ReviewPopulation.REMOVED) : null);
	}

	private ReviewReportModel.PopulationSection section(Task task, ReviewPopulation population) {
		Optional<AccountReviewAttestation> attestation = this.attestations.findByTaskIdAndPopulation(task.getId(),
				population);
		return new ReviewReportModel.PopulationSection(attestation.isPresent(),
				attestation.map(AccountReviewAttestation::getConfirmedBy).orElse(null),
				attestation.map(AccountReviewAttestation::getConfirmedAt).orElse(null),
				attestation.map(AccountReviewAttestation::getNote).orElse(null), populationRows(task, population));
	}

	private static ReviewReportModel.DepartmentRow departmentRow(String department, long[] counts) {
		return new ReviewReportModel.DepartmentRow(department, counts[AccountReviewOutcome.CONFIRMED.ordinal()],
				counts[AccountReviewOutcome.CONFIRMED_ROLES_EDITED.ordinal()],
				counts[AccountReviewOutcome.REMOVED.ordinal()], counts[AccountReviewOutcome.PENDING.ordinal()],
				counts[4]);
	}

	private static String displayName(AccountReviewItem item) {
		return item.isPending() && item.getUser() != null ? item.getUser().getName() : item.getFullName();
	}

	private static String outcomeLabel(AccountReviewOutcome outcome) {
		return switch (outcome) {
			case PENDING -> "Pending";
			case CONFIRMED -> "Confirmed";
			case CONFIRMED_ROLES_EDITED -> "Confirmed (Roles Edited)";
			case REMOVED -> "Removed";
		};
	}

	private static boolean isPrivileged(Task task) {
		return Task.PRIVILEGED_ACCOUNT_REVIEW.equals(task.getType());
	}

	/**
	 * Returns a task that has populations, which only the privileged review has.
	 */
	private Task privilegedTask(UUID taskId) {
		Task task = task(taskId);
		if (!isPrivileged(task)) {
			throw new BadRequestException("Only the privileged account review has populations.");
		}
		return task;
	}

	private Task openTask(UUID taskId) {
		Task task = task(taskId);
		if (!task.isOpen()) {
			throw new ConflictException("The review task is completed.");
		}
		return task;
	}

	private void rejectOwnAccount(Collection<AccountReviewItem> found, String actor, UUID taskId) {
		for (AccountReviewItem item : found) {
			if (item.getUsername().equals(actor)) {
				this.auditLogger.record("review_rejected", "REVIEW", item.getPublicId().toString(), item.getUsername(),
						"own_account", null, Map.of("taskId", taskId));
				throw new AccessDeniedException("Reviewers cannot review their own account.");
			}
		}
	}

	/**
	 * Returns whether the item is pending and its account exists and is active.
	 */
	private static boolean decidable(AccountReviewItem item) {
		return item != null && item.isPending() && item.getUser() != null
				&& item.getUser().getStatus() == AccountStatus.ACTIVE;
	}

	private List<ReviewItemResponse> itemRows(Task task) {
		String actor = Auditor.current();
		return this.items.findAllWithAccount(task.getId())
			.stream()
			.filter(AccountReviewService::inActiveCategory)
			.map(item -> row(item, actor))
			.toList();
	}

	private static boolean inActiveCategory(AccountReviewItem item) {
		return switch (item.getOutcome()) {
			case CONFIRMED, CONFIRMED_ROLES_EDITED -> true;
			case PENDING -> item.getUser() != null && item.getUser().getStatus() == AccountStatus.ACTIVE;
			case REMOVED -> false;
		};
	}

	private static ReviewItemResponse row(AccountReviewItem item, String actor) {
		boolean own = item.getUsername().equals(actor);
		if (item.isPending()) {
			AppUser user = item.getUser();
			return new ReviewItemResponse(item.getPublicId(), item.getUserPublicId(), item.getUsername(),
					user.getName(), user.getDepartment(), ReviewItems.roleNames(user), null,
					item.getPrivilegedPermissions(), user.getLastLoginAt(), item.getOutcome().value(), null, own, null,
					null);
		}
		return new ReviewItemResponse(item.getPublicId(), item.getUserPublicId(), item.getUsername(),
				item.getFullName(), item.getDepartment(), item.getRolesAfter(), item.getRolesBefore(),
				item.getPrivilegedPermissions(), item.getLastLoginAt(), item.getOutcome().value(),
				ReviewRemarks.remark(item.getOutcome(), item.getRolesBefore(), item.getRolesAfter(), null), own,
				item.getDecidedBy(), item.getDecidedAt());
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

	/**
	 * Returns a population as it is now, or as it was when confirmed.
	 */
	List<PopulationEntryResponse> populationRows(Task task, ReviewPopulation population) {
		Optional<AccountReviewAttestation> attestation = this.attestations.findByTaskIdAndPopulation(task.getId(),
				population);
		if (attestation.isPresent()) {
			return this.entries.findByAttestationId(attestation.get().getId())
				.stream()
				.map(entry -> new PopulationEntryResponse(entry.getUserPublicId(), entry.getUsername(),
						entry.getFullName(), entry.getDepartment(), entry.getLastLoginAt(), entry.getOccurredAt(),
						entry.getActor(), entry.getReasonCode(), entry.getReasonNote()))
				.toList();
		}
		return population == ReviewPopulation.SUSPENDED ? liveSuspended() : liveRemoved(task);
	}

	private List<PopulationEntryResponse> liveSuspended() {
		List<AppUser> suspended = this.users.findByStatus(AccountStatus.SUSPENDED);
		Map<String, AccountAuditEvent> latest = new HashMap<>();
		if (!suspended.isEmpty()) {
			for (AccountAuditEvent event : this.auditEvents.findByActionAndTargetIdIn("suspend_user",
					suspended.stream().map(user -> user.getPublicId().toString()).toList())) {
				latest.merge(event.getTargetId(), event,
						(first, second) -> second.getOccurredAt().isAfter(first.getOccurredAt()) ? second : first);
			}
		}
		return suspended.stream()
			.map(user -> new PopulationEntryResponse(user.getPublicId(), user.getUsername(), user.getName(),
					user.getDepartment(), user.getLastLoginAt(), user.getSuspendedAt(),
					Optional.ofNullable(latest.get(user.getPublicId().toString()))
						.map(AccountAuditEvent::getActor)
						.orElse(null),
					user.getSuspensionReasonCode(), user.getSuspensionNote()))
			.toList();
	}

	/**
	 * Returns the removals since the previous task's removed population was confirmed, or
	 * since that task started if it never was, or every recorded removal for the first
	 * task, so no removal falls between two reviews.
	 */
	private List<PopulationEntryResponse> liveRemoved(Task task) {
		Instant since = this.tasks
			.findFirstByTypeAndStartDateBeforeOrderByStartDateDesc(task.getType(), task.getStartDate())
			.map(previous -> this.attestations.findByTaskIdAndPopulation(previous.getId(), ReviewPopulation.REMOVED)
				.map(AccountReviewAttestation::getConfirmedAt)
				.orElseGet(() -> previous.getStartDate().atStartOfDay(this.zone).toInstant()))
			.orElse(null);
		Specification<AccountAuditEvent> specification = (root, query, builder) -> builder
			.and(builder.equal(root.get("action"), "delete_user"), builder.equal(root.get("targetType"), "USER"));
		if (since != null) {
			specification = specification
				.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), since));
		}
		return this.auditEvents.findAll(specification, Sort.by("occurredAt"))
			.stream()
			.map(AccountReviewService::removedEntry)
			.toList();
	}

	private static PopulationEntryResponse removedEntry(AccountAuditEvent event) {
		Map<String, Object> details = event.getDetails() == null ? Map.of()
				: JSON.readValue(event.getDetails(), DETAILS);
		Object lastLogin = details.get("lastLoginAt");
		return new PopulationEntryResponse(UUID.fromString(event.getTargetId()), event.getTargetName(),
				event.getTargetFullName(), (String) details.get("department"),
				lastLogin == null ? null : Instant.parse(lastLogin.toString()), event.getOccurredAt(), event.getActor(),
				event.getReasonCode(), event.getReasonNote());
	}

	private static PopulationStatus status(AccountReviewAttestation attestation) {
		return attestation == null ? new PopulationStatus(false, null, null, null, null)
				: new PopulationStatus(true, attestation.getConfirmedBy(), attestation.getConfirmedAt(),
						attestation.getNote(), attestation.getEntryCount());
	}

	private LocalDate today() {
		return LocalDate.ofInstant(this.clock.instant(), this.zone);
	}

}
