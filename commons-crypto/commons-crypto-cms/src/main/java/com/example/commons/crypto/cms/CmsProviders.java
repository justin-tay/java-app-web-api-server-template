package com.example.commons.crypto.cms;

import java.security.Provider;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * The Bouncy Castle provider, used by instance so that the application's registered
 * providers are not changed.
 */
final class CmsProviders {

	static final Provider BC = new BouncyCastleProvider();

	private CmsProviders() {
	}

}
