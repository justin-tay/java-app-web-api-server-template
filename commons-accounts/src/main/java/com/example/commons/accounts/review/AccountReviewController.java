package com.example.commons.accounts.review;

import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.admin.AdminDtos.AccountActionRequest;
import com.example.commons.accounts.admin.AdminDtos.PageResponse;
import com.example.commons.accounts.admin.AdminPageable;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewStatus;
import com.example.commons.accounts.review.AccountReviewService.Category;
import com.example.commons.accounts.review.ReviewDtos.DecisionRequest;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskResponse;
import com.example.commons.accounts.validation.ResourceId;
import com.example.commons.web.problem.BadRequestException;

/**
 * Working through an account review task, for account reviewers.
 */
@RestController
@Validated
@RequestMapping("/account-reviews/tasks/{taskId}")
@PreAuthorize("hasRole('ACCOUNT_REVIEWER')")
public class AccountReviewController {

	private final AccountReviewService service;

	public AccountReviewController(AccountReviewService service) {
		this.service = service;
	}

	@GetMapping
	public TaskResponse get(@PathVariable @ResourceId String taskId) {
		return this.service.response(this.service.task(taskId));
	}

	@GetMapping("/items")
	public PageResponse<ReviewItemResponse> items(@PathVariable @ResourceId String taskId,
			@RequestParam @NotNull @Pattern(regexp = "active|suspended|removed") String category,
			@RequestParam(required = false) @Pattern(
					regexp = "pending_verification|verified|removed") String reviewStatus,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<ReviewItemResponse> result = this.service.items(taskId,
				Category.valueOf(category.toUpperCase(Locale.ROOT)),
				reviewStatus == null ? null : ReviewStatus.fromValue(reviewStatus), search,
				AdminPageable.create(page, size, request.getParameterValues("sort"),
						Set.of("username", "name", "lastLoginAt", "suspendedAt", "removedAt"), "username"));
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	/**
	 * Verifies or removes one or more items, all or none.
	 * @param taskId the task
	 * @param request the items and the decision; a removal needs a reason code
	 * @return no content
	 */
	@PostMapping("/decisions")
	public ResponseEntity<Void> decide(@PathVariable @ResourceId String taskId,
			@Valid @RequestBody DecisionRequest request) {
		boolean verify = request.decision().equals("verify");
		if (!verify && request.reasonCode() == null) {
			throw new BadRequestException("A removal needs a reason code.");
		}
		this.service.decide(taskId, request.itemIds(), verify,
				verify ? null : ReasonCode.fromValue(request.reasonCode()), request.note());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/items/{itemId}/suspend")
	public ResponseEntity<Void> suspend(@PathVariable @ResourceId String taskId,
			@PathVariable @ResourceId String itemId, @Valid @RequestBody AccountActionRequest request) {
		this.service.suspend(taskId, itemId, ReasonCode.fromValue(request.reasonCode()), request.note());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/items/{itemId}/unsuspend")
	public ResponseEntity<Void> unsuspend(@PathVariable @ResourceId String taskId,
			@PathVariable @ResourceId String itemId) {
		this.service.unsuspend(taskId, itemId);
		return ResponseEntity.noContent().build();
	}

}
