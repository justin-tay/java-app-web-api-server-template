package com.example.commons.crypto;

import java.security.Provider;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * The Bouncy Castle provider that the modules use. One instance is created the first time
 * it is needed and shared. It is never added to the JDK's provider list, so the
 * application's own providers are not changed, and the application does not need to
 * register Bouncy Castle.
 */
public final class BouncyCastle {

	private BouncyCastle() {
	}

	/**
	 * The shared provider instance, which is safe to use from several threads.
	 * @return the Bouncy Castle provider
	 */
	public static Provider provider() {
		return Holder.PROVIDER;
	}

	private static final class Holder {

		static final Provider PROVIDER = new BouncyCastleProvider();

	}

}
