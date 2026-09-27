package com.example.commons.accounts;

import java.util.Optional;

import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authentication.passkey.PasskeyUserDirectory;

/**
 * Finds the enabled local user a passkey is registered to. The user's {@code id}, a
 * random UUID that never changes, is the WebAuthn user handle (see docs/adr/0024).
 */
public class AppUserPasskeyUserDirectory implements PasskeyUserDirectory {

	private final AppUserRepository users;

	public AppUserPasskeyUserDirectory(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<PasskeyUser> findByUsername(String username) {
		return this.users.findByUsernameAndEnabledTrue(username)
			.map(user -> new PasskeyUser(user.getId(), user.getUsername(), user.getDisplayName()));
	}

}
