package com.example.commons.accounts.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.admin.AdminDtos.PageResponse;
import com.example.commons.audit.AuditOutcome;
import com.example.commons.audit.AuditRecord;
import com.example.commons.audit.AuditSearch;
import com.example.commons.audit.AuditTrail;

/**
 * Reads the audit trail (see docs/adr/0040), for whoever holds {@code audit:read}. It is
 * read-only: nothing here changes or deletes an event. Refused attempts are listed with
 * the outcome {@code failure} beside the changes that were made.
 */
@RestController
@Validated
@RequestMapping("/audit-events")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditEventController {

	private final AuditTrail trail;

	public AuditEventController(AuditTrail trail) {
		this.trail = trail;
	}

	/**
	 * One audit event, with its details as an object.
	 */
	public record AuditEventResponse(UUID id, Instant occurredAt, String actor, String action, String outcome,
			String targetType, String targetId, String targetName, String targetFullName, String reasonCode,
			String reasonNote, Object details) {
	}

	@GetMapping
	public PageResponse<AuditEventResponse> list(@RequestParam(required = false) @Size(max = 100) String actor,
			@RequestParam(required = false) @Pattern(regexp = "USER|ROLE|SETTING|REVIEW") String targetType,
			@RequestParam(required = false) @Size(max = 100) String targetName,
			@RequestParam(required = false) @Size(max = 50) String action,
			@RequestParam(required = false) @Pattern(regexp = "success|failure") String outcome,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		AuditSearch search = new AuditSearch(actor, targetType, targetName, action,
				(outcome != null) ? AuditOutcome.valueOf(outcome.toUpperCase(Locale.ROOT)) : null,
				(occurredFrom != null) ? occurredFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : null,
				(occurredTo != null) ? occurredTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null);
		Page<AuditRecord> result = this.trail.search(search, AdminPageable.create(page, size,
				request.getParameterValues("sort"), Set.of("occurredAt"), "occurredAt,desc"));
		return new PageResponse<>(result.map(AuditEventController::response).toList(), result.getNumber(),
				result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	private static AuditEventResponse response(AuditRecord event) {
		return new AuditEventResponse(event.id(), event.occurredAt(), event.actor(), event.action(),
				event.outcome().value(), event.target().type(), event.target().id(), event.target().name(),
				event.target().fullName(), event.reasonCode(), event.reasonNote(), event.details(Object.class));
	}

}
