package com.example.commons.security.oauth2;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Replaces Spring Boot's client registration repository, which fetches every
 * {@code issuer-uri} at startup and so keeps the application from starting while the
 * OpenID Provider is down, with {@link LazyClientRegistrationRepository} (see
 * docs/adr/0029). Applied only when a client provider has an {@code issuer-uri}.
 */
@AutoConfiguration(
		beforeName = "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ HttpSecurity.class, ClientRegistrationRepository.class })
@ConditionalOnBooleanProperty(name = "commons.security.enabled", matchIfMissing = true)
@ConditionalOnIssuerUriClientRegistration
@EnableConfigurationProperties({ OidcDiscoveryProperties.class, OAuth2ClientProperties.class })
public class OidcDiscoveryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ClientRegistrationRepository.class)
	LazyClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties clientProperties,
			OidcDiscoveryProperties properties, ProviderTrust providerTrust, ObjectProvider<Clock> clock) {
		LazyClientRegistrationRepository repository = new LazyClientRegistrationRepository(clientProperties,
				new OidcDiscoveryClient(properties.getConnectTimeout(), properties.getReadTimeout(),
						issuerUri -> providerTrust.forIssuerUri(issuerUri).orElse(null)),
				properties.getRetryInterval(), clock.getIfAvailable(Clock::systemUTC));
		repository.resolveAtStartup();
		return repository;
	}

	/**
	 * Reports discovery to the readiness group when actuator health is present.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	static class OidcDiscoveryHealthConfiguration {

		/**
		 * Reports the lazy repository's state. An application that defines its own
		 * {@link ClientRegistrationRepository} still gets the contributor the readiness
		 * group names; it is UP, as nothing is discovered lazily.
		 * @param repository the client registrations
		 * @return the health indicator
		 */
		@Bean
		OidcDiscoveryHealthIndicator oidcDiscoveryHealthIndicator(
				ObjectProvider<ClientRegistrationRepository> repository) {
			return new OidcDiscoveryHealthIndicator(
					(repository.getIfAvailable() instanceof LazyClientRegistrationRepository lazy) ? lazy : null);
		}

	}

}
