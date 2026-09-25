package com.example.commons;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;

/**
 * Runtime hints for a GraalVM native image of any application on commons, registered
 * through {@code META-INF/spring/aot.factories} so no application has to import them.
 * Each covers a use AOT's static analysis cannot see, without which the native image
 * fails to start:
 * <ul>
 * <li>{@link CommonsDefaultsEnvironmentPostProcessor} reads
 * {@value CommonsDefaultsEnvironmentPostProcessor#DEFAULTS_LOCATION} as a classpath
 * resource before the application context exists.</li>
 * <li>The {@code authenticationEventPublisher} of
 * {@link com.example.commons.security.WebSecurityAutoConfiguration} sets
 * {@link AbstractAuthenticationFailureEvent} as the default failure event, whose
 * constructor Spring Security's {@code DefaultAuthenticationEventPublisher} looks up
 * reflectively.</li>
 * </ul>
 */
class CommonsRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerPattern(CommonsDefaultsEnvironmentPostProcessor.DEFAULTS_LOCATION);
		hints.reflection()
			.registerType(AbstractAuthenticationFailureEvent.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
	}

}
