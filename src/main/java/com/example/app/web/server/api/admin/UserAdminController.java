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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.app.web.server.domain.AppUser;

@RestController
@Validated
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('USER_MANAGE')")
public class UserAdminController {

	private final AdministrationService service;

	public UserAdminController(AdministrationService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
		AppUser user = this.service.createUser(request);
		return ResponseEntity.created(URI.create("/admin/users/" + user.getId())).body(response(user));
	}

	@GetMapping
	public PageResponse<UserResponse> list(@RequestParam(required = false) String username,
			@RequestParam(required = false) String displayName, @RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) String groupId, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) String sort) {
		Page<AppUser> result = this.service.users(username, displayName, enabled, groupId, AdminPageable.create(page,
				size, sort, Set.of("username", "displayName", "createdAt", "updatedAt"), "username"));
		return new PageResponse<>(result.map(this::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	public UserResponse get(@PathVariable String id) {
		return response(this.service.user(id));
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable String id, @Valid @RequestBody UserUpdateRequest request) {
		return response(this.service.updateUser(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		this.service.deleteUser(id);
		return ResponseEntity.noContent().build();
	}

	private UserResponse response(AppUser user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
				user.isEnabled(),
				user.getGroups().stream().map(group -> new Summary(group.getId(), group.getName())).toList());
	}

}
