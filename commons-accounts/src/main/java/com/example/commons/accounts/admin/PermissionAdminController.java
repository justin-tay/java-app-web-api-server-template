package com.example.commons.accounts.admin;

import static com.example.commons.accounts.admin.AdminDtos.*;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.domain.AppPermission;

/**
 * Lists the permissions. They are reference data that the schema seeds, because the code
 * checks them by name, so there is nothing to create, change or delete here (see
 * docs/adr/0038).
 */
@RestController
@Validated
@RequestMapping("/admin/permissions")
@PreAuthorize("hasAuthority('permission:read')")
public class PermissionAdminController {

	private final AdministrationService service;

	public PermissionAdminController(AdministrationService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<PermissionResponse> list(@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @Size(max = 100) String domain,
			@RequestParam(required = false) Boolean privileged, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<AppPermission> result = this.service.permissions(search, domain, privileged, AdminPageable.create(page,
				size, request.getParameterValues("sort"), Set.of("domain", "action", "privileged"), "domain"));
		return new PageResponse<>(result.map(PermissionAdminController::response).toList(), result.getNumber(),
				result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	public PermissionResponse get(@PathVariable UUID id) {
		return response(this.service.permission(id));
	}

	private static PermissionResponse response(AppPermission permission) {
		return new PermissionResponse(permission.getPublicId(), permission.getDomain(), permission.getAction(),
				permission.getName(), permission.isPrivileged());
	}

}
