package com.example.commons.accounts.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import com.example.commons.accounts.admin.AdminDtos.PageResponse;
import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AccountAuditEventRepository;

/**
 * Reads the business audit trail (see docs/adr/0030), for account reviewers and user
 * administrators. It is read-only: nothing here changes or deletes an event.
 */
@RestController
@Validated
@RequestMapping("/audit-events")
@PreAuthorize("hasAnyRole('ACCOUNT_REVIEWER', 'USER_MANAGE')")
public class AuditEventController {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AccountAuditEventRepository events;

	public AuditEventController(AccountAuditEventRepository events) {
		this.events = events;
	}

	/**
	 * One audit event, with its details as an object.
	 */
	public record AuditEventResponse(UUID id, Instant occurredAt, String actor, String action, String targetType,
			String targetId, String targetName, String targetFullName, String reasonCode, String reasonNote,
			Object details) {
	}

	@GetMapping
	public PageResponse<AuditEventResponse> list(@RequestParam(required = false) @Size(max = 100) String actor,
			@RequestParam(required = false) @Pattern(regexp = "USER|GROUP|ROLE|SETTING|REVIEW") String targetType,
			@RequestParam(required = false) @Size(max = 100) String targetName,
			@RequestParam(required = false) @Size(max = 50) String action,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Specification<AccountAuditEvent> specification = Specification.unrestricted();
		specification = and(specification, "actor", actor);
		specification = and(specification, "targetType", targetType);
		specification = and(specification, "targetName", targetName);
		specification = and(specification, "action", action);
		if (occurredFrom != null) {
			Instant from = occurredFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
			specification = specification
				.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
		}
		if (occurredTo != null) {
			Instant to = occurredTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
			specification = specification.and((root, query, builder) -> builder.lessThan(root.get("occurredAt"), to));
		}
		Page<AccountAuditEvent> result = this.events.findAll(specification, AdminPageable.create(page, size,
				request.getParameterValues("sort"), Set.of("occurredAt"), "occurredAt,desc"));
		return new PageResponse<>(result.map(AuditEventController::response).toList(), result.getNumber(),
				result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	private static Specification<AccountAuditEvent> and(Specification<AccountAuditEvent> specification, String field,
			String value) {
		if (value == null || value.isBlank()) {
			return specification;
		}
		String pattern = "%" + value.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return specification.and((root, query, builder) -> builder.like(builder.lower(root.get(field)), pattern, '\\'));
	}

	private static AuditEventResponse response(AccountAuditEvent event) {
		Object details = event.getDetails() == null ? null : JSON.readValue(event.getDetails(), Object.class);
		return new AuditEventResponse(event.getId(), event.getOccurredAt(), event.getActor(), event.getAction(),
				event.getTargetType(), event.getTargetId(), event.getTargetName(), event.getTargetFullName(),
				event.getReasonCode(), event.getReasonNote(), details);
	}

}
