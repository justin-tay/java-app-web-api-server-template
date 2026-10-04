package com.example.commons.security.authentication.passkey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import com.example.commons.security.authorization.LocalAuthorityRefresher;
import com.example.commons.security.authorization.RolePrefix;

/**
 * Refreshes the authorities of a passkey login, keyed by the username of its passkey user
 * entity, so a passkey session is revoked when its local user is disabled or deleted, and
 * follows changes to the user's roles, exactly as an OpenID Connect session does.
 */
class PasskeyLocalAuthorityRefresher implements LocalAuthorityRefresher {

	@Override
	public boolean supports(Authentication authentication) {
		return authentication instanceof WebAuthnAuthentication;
	}

	@Override
	public String username(Authentication authentication) {
		return authentication.getName();
	}

	@Override
	public Authentication refresh(Authentication authentication, Collection<GrantedAuthority> localAuthorities) {
		WebAuthnAuthentication webAuthn = (WebAuthnAuthentication) authentication;
		Set<GrantedAuthority> authorities = webAuthn.getAuthorities()
			.stream()
			.filter(authority -> !authority.getAuthority().startsWith(RolePrefix.VALUE))
			.collect(Collectors.toCollection(HashSet::new));
		authorities.addAll(localAuthorities);
		WebAuthnAuthentication refreshed = new WebAuthnAuthentication(webAuthn.getPrincipal(), authorities);
		refreshed.setDetails(webAuthn.getDetails());
		return refreshed;
	}

}
