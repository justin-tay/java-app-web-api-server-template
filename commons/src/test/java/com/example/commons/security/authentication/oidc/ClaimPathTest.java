package com.example.commons.security.authentication.oidc;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimPathTest {

	@Test
	void resolvesTopLevelClaim() {
		assertThat(ClaimPath.resolve(Map.of("preferred_username", "alice"), "preferred_username")).isEqualTo("alice");
	}

	@Test
	void resolvesNestedClaimByDottedPath() {
		Map<String, Object> claims = Map.of("xyz", Map.of("preferred_username", "alice"));

		assertThat(ClaimPath.resolve(claims, "xyz.preferred_username")).isEqualTo("alice");
	}

	@Test
	void resolvesDeeplyNestedClaim() {
		Map<String, Object> claims = Map.of("a", Map.of("b", Map.of("c", "alice")));

		assertThat(ClaimPath.resolve(claims, "a.b.c")).isEqualTo("alice");
	}

	@Test
	void prefersAClaimWhoseNameIsTheWholePath() {
		Map<String, Object> claims = Map.of("https://example.com/username", "flat", "https://example",
				Map.of("com/username", "nested"));

		assertThat(ClaimPath.resolve(claims, "https://example.com/username")).isEqualTo("flat");
	}

	@Test
	void resolvesNothingForAMissingOrNonStringClaim() {
		Map<String, Object> claims = Map.of("xyz", Map.of("count", 1), "plain", "text");

		assertThat(ClaimPath.resolve(claims, "xyz.preferred_username")).isNull();
		assertThat(ClaimPath.resolve(claims, "xyz.count")).isNull();
		assertThat(ClaimPath.resolve(claims, "xyz")).isNull();
		assertThat(ClaimPath.resolve(claims, "plain.nested")).isNull();
		assertThat(ClaimPath.resolve(claims, "missing")).isNull();
	}

}
