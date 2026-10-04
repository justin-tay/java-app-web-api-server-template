package com.example.app.web.server.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authorization.RolePrefix;

/**
 * Login user endpoint: who is signed in and what they may do, read from the local user,
 * group, and role model rather than the identity provider. A passkey login never reaches
 * Keycloak (see docs/adr/0024), so the response is built the same way, from the same
 * local user, regardless of which method the caller signed in with; {@code id} is the
 * local {@code app_user.id}, not the identity provider's {@code sub}, and is the one
 * identifier stable across both login methods.
 */
@RestController
public class LoginUserController {

	private final AppUserRepository users;

	public LoginUserController(AppUserRepository users) {
		this.users = users;
	}

	/**
	 * The fields returned to the caller.
	 *
	 * @param id the local user's id, stable across both OIDC and passkey logins
	 * @param username the username
	 * @param name the name
	 * @param email the email address, when the user has one
	 * @param roles the caller's {@code ROLE_} authorities, exactly as
	 * {@code hasAuthority()} checks them, for the frontend to decide which routes to show
	 */
	public record LoginUserResponse(UUID id, String username, String name, String email, List<String> roles) {
	}

	@GetMapping(path = "/login-user", produces = MediaType.APPLICATION_JSON_VALUE)
	public LoginUserResponse loginUser(Authentication authentication) {
		// LocalAuthorityRefreshFilter deauthenticates a disabled or deleted user on every
		// request, so the local user for an authenticated caller always exists here.
		AppUser user = this.users.findByUsernameAndStatus(authentication.getName(), AccountStatus.ACTIVE).orElseThrow();
		List<String> roles = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority.startsWith(RolePrefix.VALUE))
			.sorted()
			.toList();
		return new LoginUserResponse(user.getId(), user.getUsername(), user.getName(), user.getEmail(), roles);
	}

}
