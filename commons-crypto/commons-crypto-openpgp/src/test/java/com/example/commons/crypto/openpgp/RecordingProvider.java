package com.example.commons.crypto.openpgp;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A provider that hands out another provider's services and notes which kinds were asked
 * for, to show that a key handle's own provider is the one that is used.
 */
final class RecordingProvider extends Provider {

	private static final long serialVersionUID = 1L;

	private final transient Provider delegate;

	final List<String> requests = Collections.synchronizedList(new ArrayList<>());

	RecordingProvider(Provider delegate) {
		super("recording", "1", "records what is asked of " + delegate.getName());
		this.delegate = delegate;
	}

	@Override
	public synchronized Service getService(String type, String algorithm) {
		this.requests.add(type);
		return this.delegate.getService(type, algorithm);
	}

}
