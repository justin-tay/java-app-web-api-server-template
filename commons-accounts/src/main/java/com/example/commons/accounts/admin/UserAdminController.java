package com.example.commons.accounts.admin;

import static com.example.commons.accounts.admin.AdminDtos.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.example.commons.accounts.validation.ResourceId;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.UserStatus;

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
	public PageResponse<UserResponse> list(@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @Size(max = 100) String username,
			@RequestParam(required = false) @Size(max = 100) String name,
			@RequestParam(required = false) @Size(max = 100) String email,
			@RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) @Pattern(regexp = "active|disabled|pending") String status,
			@RequestParam(required = false) @ResourceId String groupId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<AppUser> result = this.service.users(
				new AdministrationService.UserQuery(search, username, name, email, enabled,
						status == null ? null : UserStatus.fromValue(status), groupId, createdFrom, createdTo),
				AdminPageable.create(page, size, request.getParameterValues("sort"),
						Set.of("username", "name", "lastLoginAt", "createdAt", "updatedAt"), "username"));
		return new PageResponse<>(result.map(this::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@GetMapping("/{id}")
	public UserResponse get(@PathVariable @ResourceId String id) {
		return response(this.service.user(id));
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable @ResourceId String id, @Valid @RequestBody UserUpdateRequest request) {
		return response(this.service.updateUser(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable @ResourceId String id) {
		this.service.deleteUser(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Ends every session of one user without changing their account.
	 * @param id the user ID
	 * @return no content
	 */
	@DeleteMapping("/{id}/sessions")
	public ResponseEntity<Void> revokeSessions(@PathVariable @ResourceId String id) {
		this.service.revokeSessions(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Ends every user's sessions except the caller's, for incident response.
	 * @return no content
	 */
	@DeleteMapping("/sessions")
	public ResponseEntity<Void> revokeAllSessions() {
		this.service.revokeAllSessions();
		return ResponseEntity.noContent().build();
	}

	private UserResponse response(AppUser user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getName(), user.getEmail(), user.isEnabled(),
				user.getLastLoginAt(), UserStatus.of(user).value(),
				user.getGroups().stream().map(group -> new Summary(group.getId(), group.getName())).toList());
	}

}
