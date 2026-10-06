package com.example.commons.accounts.admin;

import static com.example.commons.accounts.admin.AdminDtos.*;

import java.net.URI;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.commons.accounts.domain.AppRole;

/**
 * Manages roles and the permissions they grant. Which permissions a request needs is
 * checked here for the endpoint and, for the permissions of a role, by
 * {@link AdministrationService} (see docs/adr/0038).
 */
@RestController
@Validated
@RequestMapping("/admin/roles")
public class RoleAdminController {

	private final AdministrationService service;

	public RoleAdminController(AdministrationService service) {
		this.service = service;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('role:create')")
	public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
		AppRole role = this.service.createRole(request);
		return ResponseEntity.created(URI.create("/admin/roles/" + role.getPublicId())).body(response(role));
	}

	@GetMapping
	@PreAuthorize("hasAuthority('role:read')")
	public PageResponse<RoleResponse> list(@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @Size(max = 100) String name,
			@RequestParam(required = false) UUID permissionId, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<AppRole> result = this.service.roles(search, name, permissionId, AdminPageable.create(page, size,
				request.getParameterValues("sort"), Set.of("name", "createdAt", "updatedAt"), "name"));
		return new PageResponse<>(result.map(this::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('role:read')")
	public RoleResponse get(@PathVariable UUID id) {
		return response(this.service.role(id));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('role:update', 'role:add-permission', 'role:remove-permission')")
	public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
		return response(this.service.updateRole(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('role:delete')")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		this.service.deleteRole(id);
		return ResponseEntity.noContent().build();
	}

	private RoleResponse response(AppRole role) {
		return new RoleResponse(role.getPublicId(), role.getName(),
				role.getPermissions()
					.stream()
					.map(permission -> new PermissionSummary(permission.getPublicId(), permission.getName(),
							permission.isPrivileged()))
					.sorted(Comparator.comparing(PermissionSummary::name))
					.toList());
	}

}
