package com.example.commons.security.authentication.oidc;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.RestOperations;

import com.example.commons.security.authorization.LocalAuthorityLookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

	/**
	 * Keycloak grants the {@code profile} and {@code email} scopes by default whatever
	 * the client asks for, so Spring Security calls the user info endpoint, and that call
	 * rejects a registration with no user name attribute.
	 */
	@Test
	void loadsTheUserWhenSpringSecurityCallsTheUserInfoEndpoint() {
		RestOperations restOperations = mock(RestOperations.class);
		given(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
			.willReturn(ResponseEntity.ok(Map.of("sub", "subject", "preferred_username", "alice")));
		DefaultOAuth2UserService userInfoService = new DefaultOAuth2UserService();
		userInfoService.setRestOperations(restOperations);
		LocalAuthoritiesOidcUserService service = new LocalAuthoritiesOidcUserService(this.lookup, userInfoService);
		Map<String, Object> claims = Map.of("sub", "subject", "preferred_username", "alice");

		OidcUser user = service.loadUser(
				request("preferred_username", claims, "http://idp/userinfo", Set.of("openid", "profile", "email")));

		assertThat(user.getName()).isEqualTo("alice");
		assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_USER");
		verify(restOperations).exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class));
	}

	private static OidcUserRequest request(String userNameAttribute, Map<String, Object> claims) {
		return request(userNameAttribute, claims, null, Set.of("openid"));
	}

	private static OidcUserRequest request(String userNameAttribute, Map<String, Object> claims, String userInfoUri,
			Set<String> grantedScopes) {
		ClientRegistration.Builder registration = ClientRegistration.withRegistrationId("idp")
			.clientId("client")
			.clientSecret("secret")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("http://localhost/login/oauth2/code/idp")
			.scope("openid")
			.authorizationUri("http://idp/authorize")
			.tokenUri("http://idp/token")
			.jwkSetUri("http://idp/jwks");
		if (userInfoUri != null) {
			registration.userInfoUri(userInfoUri);
		}
		if (userNameAttribute != null) {
			registration.userNameAttributeName(userNameAttribute);
		}
		Map<String, Object> idTokenClaims = new HashMap<>(claims);
		idTokenClaims.put("iss", "http://idp");
		OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300), idTokenClaims);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
				Instant.now(), Instant.now().plusSeconds(300), grantedScopes);
		return new OidcUserRequest(registration.build(), accessToken, idToken);
	}

}
