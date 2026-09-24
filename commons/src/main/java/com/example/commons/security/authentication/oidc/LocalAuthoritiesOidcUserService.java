package com.example.commons.security.authentication.oidc;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.example.commons.security.authorization.LocalAuthorityLookup;

/**
 * Loads the OIDC user from the identity provider and adds the application's local
 * {@code ROLE_} authorities from {@link LocalAuthorityLookup}, rejecting the login when
 * there is no enabled local user for the {@code preferred_username} claim.
 */
public class LocalAuthoritiesOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OidcUserService delegate = new OidcUserService();

	private final LocalAuthorityLookup localAuthorityLookup;

	public LocalAuthoritiesOidcUserService(LocalAuthorityLookup localAuthorityLookup) {
		this.localAuthorityLookup = localAuthorityLookup;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
		OidcUser oidcUser = this.delegate.loadUser(request);
		String username = oidcUser.getClaimAsString("preferred_username");
		if (username == null || username.isBlank()) {
			throw unauthorized();
		}
		Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
		authorities.addAll(this.localAuthorityLookup.findAuthorities(username).orElseThrow(this::unauthorized));
		return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "preferred_username");
	}

	private OAuth2AuthenticationException unauthorized() {
		return new OAuth2AuthenticationException(new OAuth2Error("local_user_not_authorized"));
	}

}
