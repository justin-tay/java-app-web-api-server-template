package com.example.commons.accounts.admin;

import static com.example.commons.accounts.admin.AdminDtos.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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

import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReasonCode;
import com.example.commons.accounts.domain.AccountStatus;

@RestController
@Validated
@RequestMapping("/admin/users")
public class UserAdminController {

	private final AdministrationService service;

	private final AccountLifecycleService lifecycle;

	public UserAdminController(AdministrationService service, AccountLifecycleService lifecycle) {
		this.service = service;
		this.lifecycle = lifecycle;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('user:create')")
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
		AppUser user = this.service.createUser(request);
		return ResponseEntity.created(URI.create("/admin/users/" + user.getPublicId())).body(response(user));
	}

	@GetMapping
	@PreAuthorize("hasAuthority('user:read')")
	public PageResponse<UserResponse> list(@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @Size(max = 100) String username,
			@RequestParam(required = false) @Size(max = 100) String name,
			@RequestParam(required = false) @Size(max = 100) String email,
			@RequestParam(required = false) @Size(max = 100) String department,
			@RequestParam(required = false) @Pattern(regexp = "active|suspended") String status,
			@RequestParam(required = false) Boolean neverSignedIn, @RequestParam(required = false) UUID roleId,
			@RequestParam(required = false) Boolean privileged,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
		Page<AppUser> result = this.service.users(
				new AdministrationService.UserQuery(search, username, name, email, department,
						status == null ? null : AccountStatus.valueOf(status.toUpperCase(Locale.ROOT)), neverSignedIn,
						roleId, privileged, createdFrom, createdTo),
				AdminPageable.create(page, size, request.getParameterValues("sort"),
						Set.of("username", "name", "department", "lastLoginAt", "createdAt", "updatedAt"), "username"));
		return new PageResponse<>(result.map(this::response).toList(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	/**
	 * Lists the distinct departments in use, for a filter control.
	 */
	@GetMapping("/departments")
	@PreAuthorize("hasAuthority('user:read')")
	public List<String> departments() {
		return this.service.departments();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('user:read')")
	public UserResponse get(@PathVariable UUID id) {
		return response(this.service.user(id));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('user:update', 'user:add-role', 'user:remove-role')")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
		return response(this.service.updateUser(id, request));
	}

	/**
	 * Suspends an account, which can then no longer sign in.
	 * @param id the user ID
	 * @param request the reason
	 * @return no content
	 */
	@PostMapping("/{id}/suspend")
	@PreAuthorize("hasAuthority('user:suspend')")
	public ResponseEntity<Void> suspend(@PathVariable UUID id, @Valid @RequestBody AccountActionRequest request) {
		this.lifecycle.suspend(id, ReasonCode.fromValue(request.reasonCode()), request.note());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/unsuspend")
	@PreAuthorize("hasAuthority('user:unsuspend')")
	public ResponseEntity<Void> unsuspend(@PathVariable UUID id) {
		this.lifecycle.unsuspend(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Permanently removes an account.
	 * @param id the user ID
	 * @param request the reason
	 * @return no content
	 */
	@PostMapping("/{id}/remove")
	@PreAuthorize("hasAuthority('user:remove')")
	public ResponseEntity<Void> remove(@PathVariable UUID id, @Valid @RequestBody AccountActionRequest request) {
		this.lifecycle.remove(id, ReasonCode.fromValue(request.reasonCode()), request.note());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Ends every session of one user without changing their account.
	 * @param id the user ID
	 * @return no content
	 */
	@DeleteMapping("/{id}/sessions")
	@PreAuthorize("hasAuthority('user:revoke-session')")
	public ResponseEntity<Void> revokeSessions(@PathVariable UUID id) {
		this.service.revokeSessions(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Ends every user's sessions except the caller's, for incident response.
	 * @return no content
	 */
	@DeleteMapping("/sessions")
	@PreAuthorize("hasAuthority('user:revoke-session')")
	public ResponseEntity<Void> revokeAllSessions() {
		this.service.revokeAllSessions();
		return ResponseEntity.noContent().build();
	}

	private UserResponse response(AppUser user) {
		return new UserResponse(user.getPublicId(), user.getUsername(), user.getName(), user.getEmail(),
				user.getDepartment(), user.getLastLoginAt(), user.getStatus().name().toLowerCase(Locale.ROOT),
				user.getSuspendedAt(), user.getSuspensionReasonCode(), user.getSuspensionNote(), user.isPrivileged(),
				user.getRoles()
					.stream()
					.map(role -> new Summary(role.getPublicId(), role.getName()))
					.sorted(Comparator.comparing(Summary::name, String.CASE_INSENSITIVE_ORDER))
					.toList());
	}

}
