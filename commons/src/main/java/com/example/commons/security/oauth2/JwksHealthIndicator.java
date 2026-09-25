package com.example.commons.security.oauth2;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;

/**
 * Reports the private JWKS as the {@code jwks} health contributor, which the commons
 * defaults add to the readiness group (see docs/adr/0020). DOWN until there is a
 * {@code sig} key with a private part to sign {@code private_key_jwt} client assertions
 * with, and while any JWKS location has no keys, such as a rotated secret before its
 * first rotation, so the application receives no traffic it cannot authenticate.
 *
 * <p>
 * The details carry key IDs, counts, and times only, never key material.
 */
public class JwksHealthIndicator extends AbstractHealthIndicator {

	private final RefreshingJwks jwks;

	public JwksHealthIndicator(RefreshingJwks jwks) {
		super("JWKS health check failed");
		this.jwks = jwks;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		RefreshingJwks.State state = this.jwks.state();
		boolean up = state.signingKeyId() != null && state.emptyLocations().isEmpty();
		(up ? builder.up() : builder.down()).withDetail("signatureKeys", state.signatureKeys())
			.withDetail("encryptionKeys", state.encryptionKeys())
			.withDetail("refreshedAt", state.refreshedAt().toString());
		if (state.signingKeyId() != null) {
			builder.withDetail("signingKeyId", state.signingKeyId());
		}
		else {
			builder.withDetail("reason", "no sig key with a private part");
		}
		if (!state.emptyLocations().isEmpty()) {
			builder.withDetail("emptyLocations", state.emptyLocations());
		}
		if (state.lastRefreshFailure() != null) {
			builder.withDetail("lastRefreshFailure", state.lastRefreshFailure().toString());
		}
	}

}
