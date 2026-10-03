package com.example.commons.security.authentication.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

class ReauthenticationChallengeTest {

	@Test
	void anOidcLoginPointsBackAtTheSameProviderWithMaxAgeZero() {
		OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(mock(OAuth2User.class),
				AuthorityUtils.NO_AUTHORITIES, "corporate idp");

		assertThat(ReauthenticationChallenge.members(token)).containsExactly(Map.entry("method", "oidc"),
				Map.entry("reauthentication_uri", "/oauth2/authorization/corporate%20idp?max_age=0"));
	}

	@Test
	void aPasskeyLoginNamesTheMethodAndHasNoUri() {
		PublicKeyCredentialUserEntity principal = mock(PublicKeyCredentialUserEntity.class);
		when(principal.getName()).thenReturn("alice");
		WebAuthnAuthentication authentication = new WebAuthnAuthentication(principal, List.of());

		assertThat(ReauthenticationChallenge.members(authentication)).containsExactly(Map.entry("method", "passkey"));
	}

	@Test
	void anyOtherAuthenticationAddsNothing() {
		assertThat(ReauthenticationChallenge.members(new TestingAuthenticationToken("alice", "unused"))).isEmpty();
		assertThat(ReauthenticationChallenge.members(null)).isEmpty();
	}

}
