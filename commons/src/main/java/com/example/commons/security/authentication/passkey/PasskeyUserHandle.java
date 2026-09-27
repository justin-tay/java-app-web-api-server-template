package com.example.commons.security.authentication.passkey;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.springframework.security.web.webauthn.api.Bytes;

/**
 * Converts between a local user's UUID and its WebAuthn user handle, the 16 bytes of the
 * UUID.
 */
final class PasskeyUserHandle {

	private PasskeyUserHandle() {
	}

	static Bytes of(String userId) {
		UUID uuid = UUID.fromString(userId);
		return new Bytes(ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array());
	}

}
