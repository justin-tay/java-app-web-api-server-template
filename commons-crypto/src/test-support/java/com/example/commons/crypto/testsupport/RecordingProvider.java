package com.example.commons.crypto.testsupport;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A provider that hands out another provider's services and notes which kinds were asked for, to
 * show that a key handle's own provider is the one that is used.
 */
public final class RecordingProvider extends Provider {

	private static final long serialVersionUID = 1L;

	private final transient Provider delegate;

	/** The types of the services that were asked for, such as KeyAgreement. */
	public final List<String> requests = Collections.synchronizedList(new ArrayList<>());

	public RecordingProvider(Provider delegate) {
		super("recording", "1", "records what is asked of " + delegate.getName());
		this.delegate = delegate;
	}

	@Override
	public synchronized Service getService(String type, String algorithm) {
		this.requests.add(type);
		return this.delegate.getService(type, algorithm);
	}

}
