package com.example.commons.crypto.openpgp;

import java.security.Provider;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * The Bouncy Castle provider, used by instance so that the application's registered
 * providers are not changed.
 */
final class PgpProviders {

	static final Provider BC = new BouncyCastleProvider();

	private PgpProviders() {
	}

}
