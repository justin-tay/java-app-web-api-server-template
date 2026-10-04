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
 * <li>An application whose Liquibase changelog includes the session and OIDC session
 * registry schemas, or whose own migration tool reads the shipped SQL scripts, finds them
 * as classpath resources under {@code com/example/commons/session/jdbc} and
 * {@code com/example/commons/session/oidc/jdbc}.</li>
 * <li>The {@code authenticationEventPublisher} of
 * {@link com.example.commons.security.WebSecurityAutoConfiguration} sets
 * {@link AbstractAuthenticationFailureEvent} as the default failure event, whose
 * constructor Spring Security's {@code DefaultAuthenticationEventPublisher} looks up
 * reflectively.</li>
 * </ul>
 */
class CommonsRuntimeHints implements RuntimeHintsRegistrar {

	/** The Liquibase changelog and SQL scripts of the session schema. */
	static final String SESSION_SCHEMA_PATTERN = "com/example/commons/session/jdbc/schema*";

	/** The Liquibase changelog and SQL scripts of the OIDC session registry schema. */
	static final String OIDC_SESSION_SCHEMA_PATTERN = "com/example/commons/session/oidc/jdbc/schema*";

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerPattern(CommonsDefaultsEnvironmentPostProcessor.DEFAULTS_LOCATION);
		hints.resources().registerPattern(SESSION_SCHEMA_PATTERN);
		hints.resources().registerPattern(OIDC_SESSION_SCHEMA_PATTERN);
		hints.reflection()
			.registerType(AbstractAuthenticationFailureEvent.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
	}

}
