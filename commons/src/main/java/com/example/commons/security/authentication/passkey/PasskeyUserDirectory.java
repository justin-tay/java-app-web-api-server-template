package com.example.commons.security.authentication.passkey;

import java.util.Optional;

/**
 * Finds the enabled local user a passkey is registered to. The user's {@code id} is the
 * WebAuthn user handle: a random, immutable UUID that identifies the user, never a value
 * derived from personal data (see docs/adr/0024). Every application that enables passkeys
 * defines one, for example backed by its user repository.
 */
public interface PasskeyUserDirectory {

	/**
	 * Finds an enabled user by username.
	 * @param username the username
	 * @return the user, or empty if there is no enabled user with that username
	 */
	Optional<PasskeyUser> findByUsername(String username);

	/**
	 * A local user, as far as passkeys need to know one.
	 *
	 * @param id the user's UUID in its 36-character text form
	 * @param username the username, which never changes
	 * @param displayName the name shown to the user
	 */
	record PasskeyUser(String id, String username, String displayName) {
	}

}
