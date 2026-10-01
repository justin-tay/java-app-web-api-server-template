package com.example.commons.security.oauth2;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when at least one {@code spring.security.oauth2.client.provider.*} has an
 * {@code issuer-uri}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
@Conditional(ConditionalOnIssuerUriClientRegistration.OnIssuerUriClientRegistrationCondition.class)
public @interface ConditionalOnIssuerUriClientRegistration {

	/**
	 * Condition for {@link ConditionalOnIssuerUriClientRegistration}.
	 */
	class OnIssuerUriClientRegistrationCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return matches(context.getEnvironment()) ? ConditionOutcome.match("a provider has an issuer-uri")
					: ConditionOutcome.noMatch("no provider has an issuer-uri");
		}

		/**
		 * Returns whether at least one client provider in the environment has an
		 * {@code issuer-uri}.
		 * @param environment the environment
		 * @return whether a provider has an {@code issuer-uri}
		 */
		public static boolean matches(Environment environment) {
			return Binder.get(environment)
				.bind("spring.security.oauth2.client", OAuth2ClientProperties.class)
				.map(properties -> properties.getProvider()
					.values()
					.stream()
					.anyMatch(provider -> provider.getIssuerUri() != null))
				.orElse(false);
		}

	}

}
