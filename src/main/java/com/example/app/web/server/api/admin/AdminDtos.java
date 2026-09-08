package com.example.app.web.server.api.admin;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.example.app.web.server.validation.DisplayName;
import com.example.app.web.server.validation.ResourceName;
import com.example.app.web.server.validation.Username;

public final class AdminDtos {

	private AdminDtos() {
	}

	public record UserCreateRequest(@Username String username, @DisplayName String displayName,
			@Email @Size(max = 254) String email, boolean enabled, @NotEmpty Set<String> groupIds) {
	}

	public record UserUpdateRequest(@DisplayName String displayName, @Email @Size(max = 254) String email,
			boolean enabled, @NotEmpty Set<String> groupIds) {
	}

	public record GroupRequest(@ResourceName String name, Set<String> roleIds) {
		public GroupRequest {
			roleIds = roleIds == null ? Set.of() : roleIds;
		}
	}

	public record RoleRequest(@ResourceName String name) {
	}

	public record Summary(String id, String name) {
	}

	public record UserResponse(String id, String username, String displayName, String email, boolean enabled,
			List<Summary> groups) {
	}

	public record GroupResponse(String id, String name, List<Summary> roles) {
	}

	public record RoleResponse(String id, String name) {
	}

	public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
	}

}
