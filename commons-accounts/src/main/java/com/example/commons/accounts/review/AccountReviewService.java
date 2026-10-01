package com.example.commons.accounts.review;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.Auditor;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewItem;
import com.example.commons.accounts.domain.ReviewItemRepository;
import com.example.commons.accounts.domain.ReviewStatus;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskSummary;
import com.example.commons.web.problem.ConflictException;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Creates account review tasks and lets reviewers work through them: listing tasks and
 * their items, verifying or removing accounts singly or in a batch, and suspending or
 * unsuspending an account from its item (see docs/adr/0032).
 *
 * <p>
 * The scope of a task is fixed when it is created, but every row shows the live account.
 * A decision freezes what the reviewer saw on the item. A reviewer cannot act on their
 * own account; a batch is applied entirely or not at all.
 */
@Transactional
public class AccountReviewService {

	public enum Category {

		ACTIVE, SUSPENDED, REMOVED

	}

	private final TaskRepository tasks;

	private final ReviewItemRepository items;

	private final AppUserRepository users;

	private final AccountAuditEventRepository auditEvents;

	private final AccountLifecycleService lifecycle;

	private final ReviewItems reviewItems;

	private final AccountAuditLogger auditLogger;

	private final Clock clock;

	private final ZoneId zone;

	public AccountReviewService(TaskRepository tasks, ReviewItemRepository items, AppUserRepository users,
			AccountAuditEventRepository auditEvents, AccountLifecycleService lifecycle, ReviewItems reviewItems,
			AccountAuditLogger auditLogger, Clock clock, ZoneId zone) {
		this.tasks = tasks;
		this.items = items;
		this.users = users;
		this.auditEvents = auditEvents;
		this.lifecycle = lifecycle;
		this.reviewItems = reviewItems;
		this.auditLogger = auditLogger;
		this.clock = clock;
		this.zone = zone;
	}

	/**
	 * Creates the task for a window, with one item for every account that exists now. A
	 * task with no accounts to review is complete at once.
	 * @param window the review window
	 * @return the task
	 */
	public Task createTask(ReviewWindow window) {
		Task task = this.tasks
			.saveAndFlush(new Task(Task.ACCOUNT_REVIEW, window.start(), window.due(), this.clock.instant()));
		List<ReviewItem> created = this.users.findAll()
			.stream()
			.map(user -> new ReviewItem(task.getId(), user))
			.toList();
		this.items.saveAll(created);
		this.auditLogger.record("create_review_task", "REVIEW", task.getId(), "account_review " + window.start(), null,
				null, Map.of("startDate", window.start().toString(), "dueDate", window.due().toString(), "itemCount",
						created.size()));
		this.reviewItems.completeIfFinished(task, Auditor.SYSTEM);
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
	public Task task(String id) {
		return this.tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task"));
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
		Map<String, Long> counts = new java.util.LinkedHashMap<>();
		for (ReviewStatus status : ReviewStatus.values()) {
			counts.put(status.value(), 0L);
		}
		for (Object[] row : this.items.countByStatus(task.getId())) {
			counts.put(((ReviewStatus) row[0]).value(), (Long) row[1]);
		}
		boolean overdue = task.isOpen() && task.getDueDate().isBefore(today());
		return new TaskResponse(task.getId(), task.getType(), task.getStatus().value(), task.getStartDate(),
				task.getDueDate(), task.getCompletedAt(), task.getCompletedBy(), overdue, counts);
	}

	/**
	 * Lists one category of a task's rows. Active and suspended rows are the task's items
	 * joined to the live account; removed rows are the removals recorded in the audit
	 * trail during the task's window, whoever made them.
	 * @param taskId the task
	 * @param category the category
	 * @param reviewStatus restricts active and suspended rows to one review status, or
	 * null
	 * @param search matches a username or name containing it, or null
	 * @param pageable the page and sort, whose properties are the API's names
	 * @return the page of rows
	 */
	@Transactional(readOnly = true)
	public Page<ReviewItemResponse> items(String taskId, Category category, ReviewStatus reviewStatus, String search,
			Pageable pageable) {
		Task task = task(taskId);
		if (category == Category.REMOVED) {
			return removedRows(task, search, pageable);
		}
		AccountStatus status = category == Category.ACTIVE ? AccountStatus.ACTIVE : AccountStatus.SUSPENDED;
		Specification<ReviewItem> specification = (root, query, builder) -> {
			var user = root.join("user");
			return builder.and(builder.equal(root.get("taskId"), taskId), builder.equal(user.get("status"), status));
		};
		if (reviewStatus != null) {
			specification = specification
				.and((root, query, builder) -> builder.equal(root.get("reviewStatus"), reviewStatus));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + escapeLike(search.toLowerCase()) + "%";
			specification = specification.and((root, query, builder) -> builder.or(
					builder.like(builder.lower(root.get("username")), pattern, '\\'),
					builder.like(builder.lower(root.get("name")), pattern, '\\')));
		}
		Pageable mapped = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				mapSort(pageable.getSort(), Map.of("username", "username", "name", "name", "lastLoginAt",
						"user.lastLoginAt", "suspendedAt", "user.suspendedAt")));
		return this.items.findAll(specification, mapped).map(item -> row(item, category));
	}

	private Page<ReviewItemResponse> removedRows(Task task, String search, Pageable pageable) {
		var from = task.getStartDate().atStartOfDay(this.zone).toInstant();
		var to = task.getDueDate().plusDays(1).atStartOfDay(this.zone).toInstant();
		Specification<AccountAuditEvent> specification = (root, query, builder) -> builder.and(
				builder.equal(root.get("action"), "delete_user"), builder.equal(root.get("targetType"), "USER"),
				builder.greaterThanOrEqualTo(root.get("occurredAt"), from),
				builder.lessThan(root.get("occurredAt"), to));
		if (search != null && !search.isBlank()) {
			String pattern = "%" + escapeLike(search.toLowerCase()) + "%";
			specification = specification.and((root, query, builder) -> builder.or(
					builder.like(builder.lower(root.get("targetName")), pattern, '\\'),
					builder.like(builder.lower(root.get("targetDisplayName")), pattern, '\\')));
		}
		Pageable mapped = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mapSort(pageable.getSort(),
				Map.of("username", "targetName", "name", "targetDisplayName", "removedAt", "occurredAt")));
		return this.auditEvents.findAll(specification, mapped)
			.map(event -> new ReviewItemResponse(event.getId(), event.getTargetId(), event.getTargetName(),
					event.getTargetDisplayName(), "removed", null, false, null, null, event.getOccurredAt(),
					event.getActor(), event.getReasonCode(), event.getReasonNote(), null, null));
	}

	/**
	 * Verifies or removes the given items, all or none. Removing needs a reason.
	 * @param taskId the task
	 * @param itemIds the items
	 * @param verify true to verify, false to remove
	 * @param reason the reason for a removal
	 * @param note the optional note for a removal
	 */
	public void decide(String taskId, List<String> itemIds, boolean verify, ReasonCode reason, String note) {
		Task task = task(taskId);
		if (!task.isOpen()) {
			throw new ConflictException("The review task is completed.");
		}
		Set<String> ids = new LinkedHashSet<>(itemIds);
		Map<String, ReviewItem> found = this.items.findByTaskIdAndIdIn(taskId, ids)
			.stream()
			.collect(Collectors.toMap(ReviewItem::getId, item -> item));
		String actor = Auditor.current();
		for (ReviewItem item : found.values()) {
			if (item.getUsername().equals(actor)) {
				this.auditLogger.record("review_rejected", "REVIEW", item.getId(), item.getUsername(), "own_account",
						null, Map.of("taskId", taskId));
				throw new AccessDeniedException("Reviewers cannot review their own account.");
			}
		}
		List<String> rejected = new ArrayList<>();
		Map<String, AppUser> accounts = new java.util.HashMap<>();
		for (String id : ids) {
			ReviewItem item = found.get(id);
			AppUser account = item == null ? null : this.users.findById(item.getUserId()).orElse(null);
			if (item == null || !item.isPending() || account == null) {
				rejected.add(id);
			}
			else {
				accounts.put(id, account);
			}
		}
		if (!rejected.isEmpty()) {
			throw new ConflictException(
					"These items are not in this task or are already decided: " + String.join(", ", rejected));
		}
		for (String id : ids) {
			ReviewItem item = found.get(id);
			AppUser account = accounts.get(id);
			if (verify) {
				item.decide(ReviewStatus.VERIFIED, account, actor, this.clock.instant(), null, null);
				this.items.save(item);
				this.auditLogger.record("verify_review_item", "REVIEW", item.getId(), item.getUsername(), null, null,
						Map.of("taskId", taskId));
			}
			else {
				this.lifecycle.remove(account, reason, note);
				this.auditLogger.record("remove_review_item", "REVIEW", item.getId(), item.getUsername(),
						reason.value(), note, Map.of("taskId", taskId));
			}
		}
		this.items.flush();
		this.reviewItems.completeIfFinished(task, actor);
	}

	/**
	 * Suspends the account of an item. The item's review status does not change.
	 */
	public void suspend(String taskId, String itemId, ReasonCode reason, String note) {
		this.lifecycle.suspend(item(taskId, itemId).getUserId(), reason, note);
	}

	/**
	 * Unsuspends the account of an item. The item's review status does not change.
	 */
	public void unsuspend(String taskId, String itemId) {
		this.lifecycle.unsuspend(item(taskId, itemId).getUserId());
	}

	private ReviewItem item(String taskId, String itemId) {
		return this.items.findById(itemId)
			.filter(item -> item.getTaskId().equals(taskId))
			.orElseThrow(() -> new ResourceNotFoundException("Review item"));
	}

	private ReviewItemResponse row(ReviewItem item, Category category) {
		AppUser account = item.getUser();
		boolean own = item.getUsername().equals(Auditor.current());
		return new ReviewItemResponse(item.getId(), item.getUserId(), item.getUsername(),
				account != null ? account.getName() : item.getName(),
				category.name().toLowerCase(java.util.Locale.ROOT), item.getReviewStatus().value(), own,
				account != null ? account.getLastLoginAt() : null, account != null ? account.getSuspendedAt() : null,
				null, null, account != null ? account.getSuspensionReasonCode() : null,
				account != null ? account.getSuspensionNote() : null, item.getDecidedBy(), item.getDecidedAt());
	}

	private LocalDate today() {
		return LocalDate.ofInstant(this.clock.instant(), this.zone);
	}

	/**
	 * Replaces the API's sort property names with the paths the query sorts by.
	 */
	private static Sort mapSort(Sort sort, Map<String, String> paths) {
		List<Sort.Order> orders = new ArrayList<>();
		sort.forEach(order -> orders.add(new Sort.Order(order.getDirection(), paths.get(order.getProperty()))));
		return Sort.by(orders);
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
