package com.example.commons.accounts.review;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.admin.AdminDtos.PageResponse;
import com.example.commons.accounts.admin.AdminDtos.Summary;
import com.example.commons.accounts.admin.AdminPageable;
import com.example.commons.accounts.domain.AccountReviewOutcome;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.ReviewPopulation;
import com.example.commons.accounts.domain.Task;
import com.example.commons.accounts.review.AccountReviewReports.Download;
import com.example.commons.accounts.review.AccountReviewReports.Format;
import com.example.commons.accounts.review.AccountReviewService.Decision;
import com.example.commons.accounts.review.AccountReviewService.ItemQuery;
import com.example.commons.accounts.review.AccountReviewService.PopulationQuery;
import com.example.commons.accounts.review.ReviewDtos.DecisionRequest;
import com.example.commons.accounts.review.ReviewDtos.GroupsRequest;
import com.example.commons.accounts.review.ReviewDtos.PopulationConfirmationRequest;
import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewDtos.ReviewItemResponse;
import com.example.commons.accounts.review.ReviewDtos.TaskResponse;
import com.example.commons.web.problem.BadRequestException;

/**
 * Working through an account review task, for account reviewers.
 */
@RestController
@Validated
@RequestMapping("/account-reviews")
@PreAuthorize("hasRole('ACCOUNT_REVIEWER')")
public class AccountReviewController {

	private final AccountReviewService service;

	public AccountReviewController(AccountReviewService service) {
		this.service = service;
	}

	/**
	 * Lists the groups the caller may assign when editing an account's groups.
	 */
	@GetMapping("/groups")
	public List<Summary> assignableGroups() {
		return this.service.assignableGroups();
	}

	@GetMapping("/tasks/{taskId}")
	public TaskResponse get(@PathVariable UUID taskId) {
		return this.service.response(this.service.task(taskId));
	}

	@GetMapping("/tasks/{taskId}/items")
	public PageResponse<ReviewItemResponse> items(@PathVariable UUID taskId,
			@RequestParam(required = false) @Pattern(
					regexp = "pending|confirmed|confirmed_groups_edited") String outcome,
			@RequestParam(required = false) @Size(max = 100) String department,
			@RequestParam(required = false) @Size(max = 100) String group,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<ReviewItemResponse> result = this.service.items(taskId,
				new ItemQuery(outcome == null ? null : AccountReviewOutcome.fromValue(outcome), department, group,
						search),
				AdminPageable.create(page, size, request.getParameterValues("sort"),
						Set.of("username", "name", "department", "lastLoginAt", "decidedAt"), "username"));
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	/**
	 * Lists the distinct departments shown in a task, for a filter control.
	 */
	@GetMapping("/tasks/{taskId}/departments")
	public List<String> departments(@PathVariable UUID taskId) {
		return this.service.departments(taskId);
	}

	/**
	 * Confirms or removes one or more items, all or none.
	 * @param taskId the task
	 * @param request the items and the decision; a removal needs a reason code
	 * @return no content
	 */
	@PostMapping("/tasks/{taskId}/decisions")
	public ResponseEntity<Void> decide(@PathVariable UUID taskId, @Valid @RequestBody DecisionRequest request) {
		boolean confirm = request.decision().equals("confirm");
		if (!confirm && request.reasonCode() == null) {
			throw new BadRequestException("A removal needs a reason code.");
		}
		this.service.decide(taskId, request.itemIds(), confirm ? Decision.CONFIRM : Decision.REMOVE,
				confirm ? null : ReasonCode.fromValue(request.reasonCode()), request.note());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Sets the full set of groups of an account, which confirms it.
	 */
	@PutMapping("/tasks/{taskId}/items/{itemId}/groups")
	public ResponseEntity<Void> editGroups(@PathVariable UUID taskId, @PathVariable UUID itemId,
			@Valid @RequestBody GroupsRequest request) {
		this.service.editGroups(taskId, itemId, request.groupIds());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/tasks/{taskId}/populations/{population}")
	public PageResponse<PopulationEntryResponse> population(@PathVariable UUID taskId,
			@PathVariable @Pattern(regexp = "suspended|removed") String population,
			@RequestParam(required = false) @Size(max = 100) String department,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<PopulationEntryResponse> result = this.service.population(taskId, ReviewPopulation.fromValue(population),
				new PopulationQuery(department, search), AdminPageable.create(page, size,
						request.getParameterValues("sort"), Set.of("username", "name", "occurredAt"), "username"));
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	/**
	 * Confirms a population as reviewed, once.
	 */
	@PostMapping("/tasks/{taskId}/populations/{population}/confirmation")
	public ResponseEntity<Void> confirmPopulation(@PathVariable UUID taskId,
			@PathVariable @Pattern(regexp = "suspended|removed") String population,
			@Valid @RequestBody(required = false) PopulationConfirmationRequest request) {
		this.service.confirmPopulation(taskId, ReviewPopulation.fromValue(population),
				request == null ? null : request.note());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Downloads the report: the stored PDF once the task is completed, otherwise a draft.
	 */
	@GetMapping("/tasks/{taskId}/report")
	public ResponseEntity<byte[]> report(@PathVariable UUID taskId,
			@RequestParam @NotNull @Pattern(regexp = "pdf|xlsx|csv") String format) {
		Task task = this.service.task(taskId);
		Download download = this.service.download(taskId, Format.fromValue(format));
		String name = "account-review-" + task.getStartDate().toString().substring(0, 7)
				+ (download.draft() ? "-draft" : "") + "." + download.format().extension();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.format().contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
			.body(download.content());
	}

}
