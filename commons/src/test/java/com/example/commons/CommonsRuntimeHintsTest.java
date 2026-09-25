package com.example.commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

class CommonsRuntimeHintsTest {

	private final RuntimeHints hints = registeredHints();

	@Test
	void includesTheCommonsDefaultsInANativeImage() {
		assertThat(RuntimeHintsPredicates.resource()
			.forResource(CommonsDefaultsEnvironmentPostProcessor.DEFAULTS_LOCATION)).accepts(this.hints);
	}

	@Test
	void letsSpringSecurityFindTheDefaultAuthenticationFailureEventConstructor() throws NoSuchMethodException {
		assertThat(RuntimeHintsPredicates.reflection()
			.onConstructorInvocation(AbstractAuthenticationFailureEvent.class.getConstructor(Authentication.class,
					AuthenticationException.class)))
			.accepts(this.hints);
	}

	private static RuntimeHints registeredHints() {
		RuntimeHints hints = new RuntimeHints();
		SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
			.load(RuntimeHintsRegistrar.class)
			.forEach(registrar -> registrar.registerHints(hints, CommonsRuntimeHintsTest.class.getClassLoader()));
		return hints;
	}

}
