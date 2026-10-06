package com.example.commons.accounts.review;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.admin.AdminDtos.PageResponse;
import com.example.commons.accounts.admin.AdminPageable;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.domain.TaskStatus;
import com.example.commons.accounts.review.ReviewDtos.TaskResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskSummary;

/**
 * The dashboard of tasks, for whoever holds {@code review:read}. Written against the
 * generic task so it lists other types of task without change (see docs/adr/0032).
 */
@RestController
@Validated
@RequestMapping("/tasks")
@PreAuthorize("hasAuthority('review:read')")
public class TaskController {

	private final AccountReviewService service;

	public TaskController(AccountReviewService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<TaskResponse> list(
			@RequestParam(required = false) @Pattern(regexp = "[a-z_]{1,40}") String type,
			@RequestParam(required = false) @Pattern(regexp = "open|completed") String status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<Task> result = this.service.tasks(type, status == null ? null : TaskStatus.valueOf(status.toUpperCase()),
				AdminPageable.create(page, size, request.getParameterValues("sort"),
						Set.of("startDate", "dueDate", "completedAt"), "startDate,desc"));
		return new PageResponse<>(result.map(this.service::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/summary")
	public TaskSummary summary() {
		return this.service.summary();
	}

}
