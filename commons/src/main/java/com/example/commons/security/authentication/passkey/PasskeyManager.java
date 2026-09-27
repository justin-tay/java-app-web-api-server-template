package com.example.commons.security.authentication.passkey;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Lists, renames, and removes the passkeys of a local user, for the user's own passkey
 * endpoints and for administrators. Passkeys are addressed by the user's UUID, the
 * WebAuthn user handle, and by the credential ID Spring Security uses, in base64url form.
 */
public interface PasskeyManager {

	/**
	 * Lists a user's passkeys.
	 * @param userId the user's UUID
	 * @return the passkeys, empty if the user has none
	 */
	List<Passkey> list(String userId);

	/**
	 * Renames a passkey.
	 * @param userId the UUID of the user the passkey must belong to
	 * @param credentialId the credential ID, base64url
	 * @param label the new label
	 * @return whether the user has such a passkey
	 */
	boolean rename(String userId, String credentialId, String label);

	/**
	 * Removes a passkey.
	 * @param userId the UUID of the user the passkey must belong to
	 * @param credentialId the credential ID, base64url
	 * @return whether the user has such a passkey
	 */
	boolean remove(String userId, String credentialId);

	/**
	 * Removes every passkey of a user, and the passkey user entity, which must happen
	 * when the local user is deleted because nothing else links the two.
	 * @param userId the user's UUID
	 */
	void removeAll(String userId);

	/**
	 * A registered passkey, without any of its key material.
	 *
	 * @param id the credential ID, base64url
	 * @param label the label the user gave the passkey
	 * @param created when it was registered
	 * @param lastUsed when it last logged the user in
	 * @param backupEligible whether the authenticator can sync the passkey to other
	 * devices
	 * @param transports how the authenticator can be reached
	 */
	record Passkey(String id, String label, Instant created, Instant lastUsed, boolean backupEligible,
			Set<String> transports) {
	}

}
