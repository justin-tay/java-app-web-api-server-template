package com.example.commons.security.authentication.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class RecentAuthenticationTest {

	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	void anAuthenticationWithinTheMaximumAgeIsRecent() {
		assertThat(RecentAuthentication.isWithin(authenticatedAt(NOW.minus(Duration.ofMinutes(15))),
				Duration.ofMinutes(15), this.clock))
			.isTrue();
	}

	@Test
	void anAuthenticationOlderThanTheMaximumAgeIsNotRecent() {
		assertThat(RecentAuthentication.isWithin(authenticatedAt(NOW.minus(Duration.ofMinutes(16))),
				Duration.ofMinutes(15), this.clock))
			.isFalse();
	}

	@Test
	void anAuthenticationWithoutAnAuthTimeIsNotRecent() {
		assertThat(RecentAuthentication.isWithin(authenticatedAt(null), Duration.ofMinutes(15), this.clock)).isFalse();
		assertThat(RecentAuthentication.isWithin(new TestingAuthenticationToken("alice", null, "ROLE_USER"),
				Duration.ofMinutes(15), this.clock))
			.isFalse();
		assertThat(RecentAuthentication.isWithin(null, Duration.ofMinutes(15), this.clock)).isFalse();
	}

	private static OAuth2AuthenticationToken authenticatedAt(Instant authTime) {
		OidcIdToken.Builder idToken = OidcIdToken.withTokenValue("id-token")
			.subject("alice")
			.issuedAt(NOW.minusSeconds(3600))
			.expiresAt(NOW.plusSeconds(3600));
		if (authTime != null) {
			idToken.authTime(authTime);
		}
		DefaultOidcUser user = new DefaultOidcUser(List.of(), idToken.build());
		return new OAuth2AuthenticationToken(user, List.of(), "keycloak");
	}

}
