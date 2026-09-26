package com.example.commons.security.session;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.session.SessionIdGenerator;

/**
 * Generates session IDs of 256 random bits from {@link SecureRandom}, encoded as 43
 * characters of unpadded base64url. Spring Session's default, a version 4 UUID, carries
 * only 122 random bits, below the 128 that OWASP ASVS V7.2.3 requires.
 */
public class SecureRandomSessionIdGenerator implements SessionIdGenerator {

	private static final int ID_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

	@Override
	public String generate() {
		byte[] bytes = new byte[ID_BYTES];
		this.random.nextBytes(bytes);
		return this.encoder.encodeToString(bytes);
	}

}
