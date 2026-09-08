package com.example.app.web.server.api.admin;

import static com.example.app.web.server.api.admin.AdminDtos.*;

import java.net.URI;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.app.web.server.domain.AppRole;

@RestController
@Validated
@RequestMapping("/admin/roles")
@PreAuthorize("hasAuthority('ROLE_ROLE_MANAGE')")
public class RoleAdminController {

	private final AdministrationService service;

	public RoleAdminController(AdministrationService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
		AppRole role = this.service.createRole(request);
		return ResponseEntity.created(URI.create("/admin/roles/" + role.getId()))
			.body(new RoleResponse(role.getId(), role.getName()));
	}

	@GetMapping
	public PageResponse<RoleResponse> list(@RequestParam(required = false) String name,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) String sort) {
		Page<AppRole> result = this.service.roles(name,
				AdminPageable.create(page, size, sort, Set.of("name", "createdAt", "updatedAt"), "name"));
		return new PageResponse<>(result.map(role -> new RoleResponse(role.getId(), role.getName())).toList(),
				result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	public RoleResponse get(@PathVariable String id) {
		AppRole role = this.service.role(id);
		return new RoleResponse(role.getId(), role.getName());
	}

	@PutMapping("/{id}")
	public RoleResponse update(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
		AppRole role = this.service.updateRole(id, request);
		return new RoleResponse(role.getId(), role.getName());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		this.service.deleteRole(id);
		return ResponseEntity.noContent().build();
	}

}
