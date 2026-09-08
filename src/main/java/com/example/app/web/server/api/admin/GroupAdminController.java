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
import com.example.app.web.server.domain.AppGroup;

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
	public PageResponse<GroupResponse> list(@RequestParam(required = false) String name,
			@RequestParam(required = false) String roleId, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) String sort) {
		Page<AppGroup> result = this.service.groups(name, roleId,
				AdminPageable.create(page, size, sort, Set.of("name", "createdAt", "updatedAt"), "name"));
		return new PageResponse<>(result.map(this::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	public GroupResponse get(@PathVariable String id) {
		return response(this.service.group(id));
	}

	@PutMapping("/{id}")
	public GroupResponse update(@PathVariable String id, @Valid @RequestBody GroupRequest request) {
		return response(this.service.updateGroup(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		this.service.deleteGroup(id);
		return ResponseEntity.noContent().build();
	}

	private GroupResponse response(AppGroup group) {
		return new GroupResponse(group.getId(), group.getName(),
				group.getRoles().stream().map(role -> new Summary(role.getId(), role.getName())).toList());
	}

}
