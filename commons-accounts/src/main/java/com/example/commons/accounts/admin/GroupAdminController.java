package com.example.commons.accounts.admin;

import static com.example.commons.accounts.admin.AdminDtos.*;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
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
import com.example.commons.accounts.domain.AppGroup;

@RestController
@Validated
@RequestMapping("/admin/groups")
@PreAuthorize("hasRole('GROUP_MANAGE')")
public class GroupAdminController {

	private final AdministrationService service;

	public GroupAdminController(AdministrationService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request) {
		AppGroup group = this.service.createGroup(request);
		return ResponseEntity.created(URI.create("/admin/groups/" + group.getId())).body(response(group));
	}

	@GetMapping
	public PageResponse<GroupResponse> list(@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @Size(max = 100) String name, @RequestParam(required = false) UUID roleId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<AppGroup> result = this.service.groups(search, name, roleId, AdminPageable.create(page, size,
				request.getParameterValues("sort"), Set.of("name", "createdAt", "updatedAt"), "name"));
		return new PageResponse<>(result.map(this::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	public GroupResponse get(@PathVariable UUID id) {
		return response(this.service.group(id));
	}

	@PutMapping("/{id}")
	public GroupResponse update(@PathVariable UUID id, @Valid @RequestBody GroupRequest request) {
		return response(this.service.updateGroup(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		this.service.deleteGroup(id);
		return ResponseEntity.noContent().build();
	}

	private GroupResponse response(AppGroup group) {
		return new GroupResponse(group.getId(), group.getName(),
				group.getRoles()
					.stream()
					.map(role -> new RoleSummary(role.getId(), role.getName(), role.getDisplayName()))
					.sorted(Comparator.comparing(RoleSummary::displayName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(RoleSummary::name))
					.toList());
	}

}
