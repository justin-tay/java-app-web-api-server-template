package com.example.app.web.server.security;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.example.app.web.server.domain.AppUserRepository;

@Service
public class LocalAuthoritiesOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OidcUserService delegate = new OidcUserService();

	private final AppUserRepository users;

	public LocalAuthoritiesOidcUserService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
		OidcUser oidcUser = this.delegate.loadUser(request);
		String username = oidcUser.getClaimAsString("preferred_username");
		if (username == null || username.isBlank()) {
			throw unauthorized();
		}
		var user = this.users.findByUsernameAndEnabledTrue(username).orElseThrow(this::unauthorized);
		Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
		user.getGroups()
			.stream()
			.flatMap(group -> group.getRoles().stream())
			.map(role -> role.getName())
			.map(role -> new SimpleGrantedAuthority("ROLE_" + role))
			.forEach(authorities::add);
		return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "preferred_username");
	}

	private OAuth2AuthenticationException unauthorized() {
		return new OAuth2AuthenticationException(new OAuth2Error("local_user_not_authorized"));
	}

}
