package com.example.commons.accounts.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.example.commons.accounts.validation.ResourceId;
import com.example.commons.accounts.validation.ResourceName;
import com.example.commons.accounts.validation.Username;

public final class AdminDtos {

	private AdminDtos() {
	}

	public record UserCreateRequest(@Username String username, @ResourceName String name,
			@Email @Size(max = 254) String email, @NotEmpty Set<@ResourceId String> groupIds) {
	}

	public record UserUpdateRequest(@ResourceName String name, @Email @Size(max = 254) String email,
			@NotEmpty Set<@ResourceId String> groupIds) {
	}

	/**
	 * The reason an account is suspended or removed. {@code inactive_account} is reserved
	 * for the application's own inactivity job, so the API does not accept it.
	 */
	public record AccountActionRequest(
			@NotNull @Pattern(regexp = "left_organisation|no_longer_required|policy_violation|other") String reasonCode,
			@Size(max = 200) String note) {
	}

	public record GroupRequest(@ResourceName String name, Set<@ResourceId String> roleIds) {
		public GroupRequest {
			roleIds = roleIds == null ? Set.of() : roleIds;
		}
	}

	public record RoleRequest(@ResourceName String name, @Size(max = 100) String displayName) {
		public RoleRequest {
			displayName = displayName == null || displayName.isBlank() ? name : displayName;
		}
	}

	public record Summary(String id, String name) {
	}

	public record RoleSummary(String id, String name, String displayName) {
	}

	public record UserResponse(String id, String username, String name, String email, Instant lastLoginAt,
			String status, Instant suspendedAt, String suspensionReasonCode, String suspensionNote,
			List<Summary> groups) {
	}

	public record GroupResponse(String id, String name, List<RoleSummary> roles) {
	}

	public record RoleResponse(String id, String name, String displayName) {
	}

	public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
	}

}
