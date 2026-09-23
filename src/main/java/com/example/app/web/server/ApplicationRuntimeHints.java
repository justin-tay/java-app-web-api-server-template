package com.example.app.web.server;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Application runtime hints.
 * <p>
 * No classpath resource needs a hint at present: the application's private JWKS is read
 * from the location in {@code app.jwks}, which a deployment supplies outside the artifact
 * (see docs/adr/0018), not from a packaged resource.
 */
public class ApplicationRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
	}

}
