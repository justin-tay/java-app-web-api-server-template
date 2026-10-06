package com.example.commons.security.authorization;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;

/**
 * Tells the authorities the application grants from its own local model from the ones the
 * login supplies itself. A local authority is a permission, such as {@code user:create},
 * with no prefix, so it is checked with {@code hasAuthority()}. The login's own are the
 * identity provider's {@code OIDC_USER}, {@code OAUTH2_USER} and {@code SCOPE_*}
 * authorities and Spring Security's {@code FACTOR_*} ones. A refresh keeps those and
 * replaces every other authority with the ones the local model grants now. They are
 * recognised by name, so this class needs no OAuth 2 classes on the classpath.
 */
public final class LocalAuthorities {

	private LocalAuthorities() {
	}

	/**
	 * Returns whether the authority comes from the local model, as opposed to the login.
	 * @param authority the authority
	 * @return whether it is local
	 */
	public static boolean isLocal(GrantedAuthority authority) {
		String value = authority.getAuthority();
		return value == null || !(value.equals("OIDC_USER") || value.equals("OAUTH2_USER") || value.startsWith("SCOPE_")
				|| value.startsWith("FACTOR_"));
	}

	/**
	 * Returns the names of the local authorities among the given ones.
	 * @param authorities the authorities of an authentication
	 * @return the local authorities as strings
	 */
	public static Set<String> names(Collection<? extends GrantedAuthority> authorities) {
		return authorities.stream()
			.filter(LocalAuthorities::isLocal)
			.map(GrantedAuthority::getAuthority)
			.collect(Collectors.toUnmodifiableSet());
	}

}
