package com.example.commons.security.oauth2;

import java.util.Map;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * Reports OpenID Provider discovery as the {@code oidcDiscovery} health contributor,
 * which the commons defaults add to the readiness group (see docs/adr/0029). DOWN until
 * every client registration with an {@code issuer-uri} has been resolved once; it then
 * stays UP, because a resolved registration is kept for the life of the process. Each
 * check retries an unresolved registration (within the retry interval), because a
 * readiness-gated application otherwise receives no login request that would.
 *
 * <p>
 * The details carry registration IDs and times only, never the issuer URI or the failure
 * text, which could name an internal host.
 */
public class OidcDiscoveryHealthIndicator extends AbstractHealthIndicator {

	private final LazyClientRegistrationRepository repository;

	/**
	 * Creates the indicator.
	 * @param repository the lazy repository, or null when the application has another
	 * kind, which has nothing to discover lazily
	 */
	public OidcDiscoveryHealthIndicator(LazyClientRegistrationRepository repository) {
		super("OIDC discovery health check failed");
		this.repository = repository;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		if (this.repository == null) {
			builder.up();
			return;
		}
		this.repository.resolveUnresolved();
		Map<String, LazyClientRegistrationRepository.State> states = this.repository.states();
		boolean up = states.values().stream().allMatch(LazyClientRegistrationRepository.State::resolved);
		builder.status(up ? Status.UP : Status.DOWN);
		states.forEach((registrationId, state) -> {
			builder.withDetail(registrationId, state.resolved() ? "resolved" : "unresolved");
			if (state.lastFailureAt() != null) {
				builder.withDetail(registrationId + "LastFailureAt", state.lastFailureAt().toString());
			}
		});
	}

}
