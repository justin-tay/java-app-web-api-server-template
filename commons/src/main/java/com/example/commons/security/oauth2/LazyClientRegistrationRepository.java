package com.example.commons.security.oauth2;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Provider;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Registration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;

/**
 * Holds the client registrations built from {@code spring.security.oauth2.client.*},
 * resolving a registration with an {@code issuer-uri} from the OpenID Provider's
 * discovery metadata on first use instead of at startup, so the application starts while
 * the provider is down (see docs/adr/0029). Boot's own property mapping still builds the
 * registration, so its properties keep their meaning: the discovered endpoints only fill
 * those the configuration leaves unset.
 *
 * <p>
 * A resolved registration is kept for the life of the process. After a failed attempt,
 * further requests fail with {@link IdentityProviderUnavailableException} until the retry
 * interval has passed, so a provider that is down is not called on every request.
 * Registrations without an {@code issuer-uri} need no network and are built at once.
 * Deliberately not {@link Iterable}: iterating would resolve every registration.
 */
public class LazyClientRegistrationRepository implements ClientRegistrationRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(LazyClientRegistrationRepository.class);

	private static final String DEFAULT_REDIRECT_URI = "{baseUrl}/{action}/oauth2/code/{registrationId}";

	private final Map<String, Entry> entries = new LinkedHashMap<>();

	private final MetadataFetcher fetcher;

	private final Duration retryInterval;

	private final Clock clock;

	public LazyClientRegistrationRepository(OAuth2ClientProperties properties, MetadataFetcher fetcher,
			Duration retryInterval, Clock clock) {
		this.fetcher = fetcher;
		this.retryInterval = retryInterval;
		this.clock = clock;
		properties.getRegistration().forEach((registrationId, registration) -> {
			Provider provider = properties.getProvider()
				.get(registration.getProvider() != null ? registration.getProvider() : registrationId);
			Entry entry = new Entry(registrationId, registration, provider);
			if (entry.issuerUri() == null) {
				entry.resolved = map(registrationId, registration, provider);
			}
			this.entries.put(registrationId, entry);
		});
	}

	/**
	 * Tries every unresolved registration once, so a definitive failure such as a
	 * mistyped {@code issuer-uri} stops startup, while an unreachable provider does not.
	 * @throws IllegalStateException when a provider's metadata is definitively invalid
	 */
	public void resolveAtStartup() {
		for (Entry entry : this.entries.values()) {
			try {
				find(entry);
			}
			catch (IdentityProviderUnavailableException ex) {
				if (entry.definitiveFailure != null) {
					throw new IllegalStateException("The discovery metadata of client registration '" + entry.id
							+ "' is not valid (" + entry.definitiveFailure + "); check its issuer-uri");
				}
			}
		}
	}

	/**
	 * Tries every unresolved registration, within the retry interval. The readiness check
	 * calls it, so an application that receives no traffic while the provider is down
	 * still finds out when the provider is back.
	 */
	public void resolveUnresolved() {
		for (Entry entry : this.entries.values()) {
			try {
				find(entry);
			}
			catch (IdentityProviderUnavailableException ex) {
				// Reported by states()
			}
		}
	}

	@Override
	public ClientRegistration findByRegistrationId(String registrationId) {
		Entry entry = this.entries.get(registrationId);
		return (entry != null) ? find(entry) : null;
	}

	/**
	 * Returns the registration IDs without resolving anything.
	 * @return the registration IDs
	 */
	public List<String> registrationIds() {
		return List.copyOf(this.entries.keySet());
	}

	/**
	 * Returns, per registration with an issuer URI, whether it has been resolved and when
	 * the last attempt failed.
	 * @return the states by registration ID
	 */
	public Map<String, State> states() {
		Map<String, State> states = new LinkedHashMap<>();
		for (Entry entry : this.entries.values()) {
			if (entry.issuerUri() != null) {
				synchronized (entry) {
					states.put(entry.id, new State(entry.resolved != null, entry.lastFailureAt));
				}
			}
		}
		return states;
	}

	private ClientRegistration find(Entry entry) {
		ClientRegistration resolved = entry.resolved;
		if (resolved != null) {
			return resolved;
		}
		synchronized (entry) {
			if (entry.resolved != null) {
				return entry.resolved;
			}
			Instant now = this.clock.instant();
			if (entry.nextAttemptAt != null && now.isBefore(entry.nextAttemptAt)) {
				throw new IdentityProviderUnavailableException(entry.id, Duration.between(now, entry.nextAttemptAt));
			}
			try {
				entry.resolved = resolve(entry);
				entry.definitiveFailure = null;
				entry.lastFailureAt = null;
				return entry.resolved;
			}
			catch (DiscoveryException ex) {
				entry.lastFailureAt = now;
				entry.nextAttemptAt = now.plus(this.retryInterval);
				entry.definitiveFailure = ex.isDefinitive() ? ex.getMessage() : null;
				LOGGER.atWarn()
					.addKeyValue("event.action", "discover_identity_provider")
					.addKeyValue("event.outcome", "failure")
					.addKeyValue("event.reason", ex.getMessage())
					.log("OpenID Provider discovery for client registration {} failed; retrying in {}", entry.id,
							this.retryInterval);
				throw new IdentityProviderUnavailableException(entry.id, this.retryInterval);
			}
		}
	}

	private ClientRegistration resolve(Entry entry) {
		String issuerUri = entry.issuerUri();
		Map<String, Object> metadata = this.fetcher.fetch(issuerUri);
		ClientRegistration discovered = ClientRegistrations.fromOidcConfiguration(metadata)
			.registrationId(entry.id)
			.clientId(entry.id)
			.redirectUri("{baseUrl}")
			.build();
		Provider provider = copy(entry.provider);
		provider.setIssuerUri(null);
		if (provider.getAuthorizationUri() == null) {
			provider.setAuthorizationUri(discovered.getProviderDetails().getAuthorizationUri());
		}
		if (provider.getTokenUri() == null) {
			provider.setTokenUri(discovered.getProviderDetails().getTokenUri());
		}
		if (provider.getJwkSetUri() == null) {
			provider.setJwkSetUri(discovered.getProviderDetails().getJwkSetUri());
		}
		if (provider.getUserInfoUri() == null) {
			provider.setUserInfoUri(discovered.getProviderDetails().getUserInfoEndpoint().getUri());
		}
		Registration registration = copy(entry.registration);
		if (registration.getRedirectUri() == null) {
			// What ClientRegistrations defaults it to for an issuer-uri registration.
			registration.setRedirectUri(DEFAULT_REDIRECT_URI);
		}
		if (registration.getClientAuthenticationMethod() == null) {
			registration.setClientAuthenticationMethod(discovered.getClientAuthenticationMethod().getValue());
		}
		ClientRegistration mapped = map(entry.id, registration, provider);
		return ClientRegistration.withClientRegistration(mapped)
			.issuerUri(issuerUri)
			.providerConfigurationMetadata(metadata)
			.build();
	}

	private static ClientRegistration map(String registrationId, Registration registration, Provider provider) {
		OAuth2ClientProperties properties = new OAuth2ClientProperties();
		properties.getRegistration().put(registrationId, registration);
		if (provider != null) {
			properties.getProvider()
				.put(registration.getProvider() != null ? registration.getProvider() : registrationId, provider);
		}
		return new OAuth2ClientPropertiesMapper(properties).asClientRegistrations().get(registrationId);
	}

	private static Provider copy(Provider source) {
		Provider copy = new Provider();
		copy.setAuthorizationUri(source.getAuthorizationUri());
		copy.setTokenUri(source.getTokenUri());
		copy.setUserInfoUri(source.getUserInfoUri());
		copy.setUserInfoAuthenticationMethod(source.getUserInfoAuthenticationMethod());
		copy.setUserNameAttribute(source.getUserNameAttribute());
		copy.setJwkSetUri(source.getJwkSetUri());
		copy.setIssuerUri(source.getIssuerUri());
		return copy;
	}

	private static Registration copy(Registration source) {
		Registration copy = new Registration();
		copy.setProvider(source.getProvider());
		copy.setClientId(source.getClientId());
		copy.setClientSecret(source.getClientSecret());
		copy.setClientAuthenticationMethod(source.getClientAuthenticationMethod());
		copy.setAuthorizationGrantType(source.getAuthorizationGrantType());
		copy.setRedirectUri(source.getRedirectUri());
		copy.setScope(source.getScope());
		copy.setClientName(source.getClientName());
		return copy;
	}

	/**
	 * The resolution state of one registration with an issuer URI.
	 *
	 * @param resolved whether its metadata has been discovered
	 * @param lastFailureAt when the last attempt failed, or null
	 */
	public record State(boolean resolved, Instant lastFailureAt) {
	}

	private static final class Entry {

		private final String id;

		private final Registration registration;

		private final Provider provider;

		private volatile ClientRegistration resolved;

		private Instant nextAttemptAt;

		private Instant lastFailureAt;

		private String definitiveFailure;

		private Entry(String id, Registration registration, Provider provider) {
			this.id = id;
			this.registration = registration;
			this.provider = provider;
		}

		private String issuerUri() {
			return (this.provider != null) ? this.provider.getIssuerUri() : null;
		}

	}

}
