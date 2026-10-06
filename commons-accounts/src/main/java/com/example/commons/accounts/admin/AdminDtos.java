package com.example.commons.accounts.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.example.commons.accounts.validation.ResourceName;
import com.example.commons.accounts.validation.Username;

public final class AdminDtos {

	private AdminDtos() {
	}

	public record UserCreateRequest(@Username String username, @ResourceName String name,
			@Email @Size(max = 254) String email, @Size(max = 100) String department, @NotEmpty Set<UUID> roleIds) {
	}

	public record UserUpdateRequest(@ResourceName String name, @Email @Size(max = 254) String email,
			@Size(max = 100) String department, @NotEmpty Set<UUID> roleIds) {
	}

	/**
	 * The reason an account is suspended or removed. {@code inactive_account} is reserved
	 * for the application's own inactivity job, so the API does not accept it.
	 */
	public record AccountActionRequest(
			@NotNull @Pattern(regexp = "left_organisation|no_longer_required|policy_violation|other") String reasonCode,
			@Size(max = 200) String note) {
	}

	public record RoleRequest(@ResourceName String name, Set<UUID> permissionIds) {
		public RoleRequest {
			permissionIds = permissionIds == null ? Set.of() : permissionIds;
		}
	}

	public record Summary(UUID id, String name) {
	}

	public record PermissionSummary(UUID id, String name, boolean privileged) {
	}

	/**
	 * A user. {@code privileged} is computed from the user's roles, not stored.
	 */
	public record UserResponse(UUID id, String username, String name, String email, String department,
			Instant lastLoginAt, String status, Instant suspendedAt, String suspensionReasonCode, String suspensionNote,
			boolean privileged, List<Summary> roles) {
	}

	public record RoleResponse(UUID id, String name, List<PermissionSummary> permissions) {
	}

	public record PermissionResponse(UUID id, String domain, String action, String name, boolean privileged) {
	}

	public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
	}

}
