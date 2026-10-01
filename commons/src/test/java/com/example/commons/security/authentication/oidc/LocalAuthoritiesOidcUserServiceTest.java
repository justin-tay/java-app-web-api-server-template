package com.example.commons.security.authentication.oidc;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.example.commons.security.authorization.LocalAuthorityLookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LocalAuthoritiesOidcUserServiceTest {

	private final LocalAuthorityLookup lookup = (username) -> "alice".equals(username)
			? Optional.<Collection<GrantedAuthority>>of(List.of(new SimpleGrantedAuthority("ROLE_USER")))
			: Optional.empty();

	private final LocalAuthoritiesOidcUserService service = new LocalAuthoritiesOidcUserService(this.lookup);

	@Test
	void usesTheSubjectWhenNoUserNameAttributeIsConfigured() {
		Map<String, Object> claims = Map.of("sub", "alice", "preferred_username", "bob");

		OidcUser user = this.service.loadUser(request(null, claims));

		assertThat(user.getName()).isEqualTo("alice");
		assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_USER");
	}

	@Test
	void usesTheConfiguredTopLevelClaim() {
		Map<String, Object> claims = Map.of("sub", "subject", "preferred_username", "alice");

		OidcUser user = this.service.loadUser(request("preferred_username", claims));

		assertThat(user.getName()).isEqualTo("alice");
	}

	@Test
	void usesTheConfiguredNestedClaim() {
		Map<String, Object> claims = Map.of("sub", "subject", "xyz", Map.of("preferred_username", "alice"));

		OidcUser user = this.service.loadUser(request("xyz.preferred_username", claims));

		assertThat(user.getName()).isEqualTo("alice");
		assertThat(user.getClaimAsString("xyz.preferred_username")).isEqualTo("alice");
		assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_USER");
	}

	@Test
	void rejectsALoginWhoseConfiguredClaimIsMissing() {
		Map<String, Object> claims = Map.of("sub", "alice");

		assertThatExceptionOfType(OAuth2AuthenticationException.class)
			.isThrownBy(() -> this.service.loadUser(request("xyz.preferred_username", claims)))
			.satisfies(ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("local_user_not_authorized"));
	}

	@Test
	void rejectsALoginWithNoEnabledLocalUser() {
		Map<String, Object> claims = Map.of("sub", "subject", "xyz", Map.of("preferred_username", "mallory"));

		assertThatExceptionOfType(OAuth2AuthenticationException.class)
			.isThrownBy(() -> this.service.loadUser(request("xyz.preferred_username", claims)))
			.satisfies(ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("local_user_not_authorized"));
	}

	private static OidcUserRequest request(String userNameAttribute, Map<String, Object> claims) {
		ClientRegistration.Builder registration = ClientRegistration.withRegistrationId("idp")
			.clientId("client")
			.clientSecret("secret")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("http://localhost/login/oauth2/code/idp")
			.scope("openid")
			.authorizationUri("http://idp/authorize")
			.tokenUri("http://idp/token")
			.jwkSetUri("http://idp/jwks");
		if (userNameAttribute != null) {
			registration.userNameAttributeName(userNameAttribute);
		}
		Map<String, Object> idTokenClaims = new HashMap<>(claims);
		idTokenClaims.put("iss", "http://idp");
		OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300), idTokenClaims);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
				Instant.now(), Instant.now().plusSeconds(300));
		return new OidcUserRequest(registration.build(), accessToken, idToken);
	}

}
