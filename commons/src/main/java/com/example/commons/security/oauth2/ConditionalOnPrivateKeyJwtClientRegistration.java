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
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Matches when at least one {@code spring.security.oauth2.client.registration.*} uses
 * {@code client-authentication-method: private_key_jwt}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
@Conditional(ConditionalOnPrivateKeyJwtClientRegistration.OnPrivateKeyJwtClientRegistrationCondition.class)
public @interface ConditionalOnPrivateKeyJwtClientRegistration {

	/**
	 * Condition for {@link ConditionalOnPrivateKeyJwtClientRegistration}.
	 */
	class OnPrivateKeyJwtClientRegistrationCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return matches(context.getEnvironment())
					? ConditionOutcome.match("a client registration uses private_key_jwt")
					: ConditionOutcome.noMatch("no client registration uses private_key_jwt");
		}

		/**
		 * Returns whether at least one client registration in the environment uses
		 * {@code private_key_jwt}.
		 * @param environment the environment
		 * @return whether a client registration uses {@code private_key_jwt}
		 */
		public static boolean matches(Environment environment) {
			return Binder.get(environment)
				.bind("spring.security.oauth2.client", OAuth2ClientProperties.class)
				.map(properties -> properties.getRegistration()
					.values()
					.stream()
					.anyMatch(registration -> ClientAuthenticationMethod.PRIVATE_KEY_JWT.getValue()
						.equalsIgnoreCase(registration.getClientAuthenticationMethod())))
				.orElse(false);
		}

	}

}
