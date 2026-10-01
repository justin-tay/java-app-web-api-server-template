package com.example.commons.security.authorization;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Refreshes the authorities of an OpenID Connect login, keyed by the user's name, which
 * is the claim that the provider's {@code user-name-attribute} names.
 */
class OidcLocalAuthorityRefresher implements LocalAuthorityRefresher {

	@Override
	public boolean supports(Authentication authentication) {
		return authentication instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof OidcUser;
	}

	@Override
	public String username(Authentication authentication) {
		return ((OidcUser) authentication.getPrincipal()).getName();
	}

	/**
	 * Finds a claim that holds the user's name, as the user does not expose which claim
	 * the provider's {@code user-name-attribute} named; any claim with the same value
	 * yields the same name.
	 */
	private static String nameAttribute(OidcUser oidcUser) {
		return oidcUser.getClaims()
			.entrySet()
			.stream()
			.filter(claim -> oidcUser.getName().equals(claim.getValue()))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No claim holds the name of the OIDC user"));
	}

	@Override
	public Authentication refresh(Authentication authentication, Collection<GrantedAuthority> localAuthorities) {
		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
		OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
		Set<GrantedAuthority> authorities = oidcUser.getAuthorities()
			.stream()
			.filter(authority -> !authority.getAuthority().startsWith("ROLE_"))
			.collect(Collectors.toCollection(HashSet::new));
		authorities.addAll(localAuthorities);
		OidcUser refreshedUser = new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(),
				nameAttribute(oidcUser));
		OAuth2AuthenticationToken refreshedToken = new OAuth2AuthenticationToken(refreshedUser, authorities,
				oauthToken.getAuthorizedClientRegistrationId());
		refreshedToken.setDetails(oauthToken.getDetails());
		return refreshedToken;
	}

}
