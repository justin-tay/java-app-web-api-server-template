package com.example.commons.security.authentication.passkey;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.commons.security.authorization.LocalAuthorityLookup;

/**
 * Supplies the local authorities of a user who logged in with a passkey, from
 * {@link LocalAuthorityLookup}, and refuses a user who is not enabled locally.
 * <p>
 * The passkey has proven who the user is, so the returned details carry a password only
 * because {@link UserDetails} requires one: it is random, unknown to anyone, and nothing
 * in this application authenticates with a password.
 */
class LocalAuthorityUserDetailsService implements UserDetailsService {

	private final LocalAuthorityLookup localAuthorityLookup;

	private final String unusablePassword;

	LocalAuthorityUserDetailsService(LocalAuthorityLookup localAuthorityLookup) {
		this.localAuthorityLookup = localAuthorityLookup;
		byte[] random = new byte[32];
		new SecureRandom().nextBytes(random);
		this.unusablePassword = "{noop}" + HexFormat.of().formatHex(random);
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return this.localAuthorityLookup.findAuthorities(username)
			.map(authorities -> User.withUsername(username)
				.password(this.unusablePassword)
				.authorities(authorities)
				.build())
			.orElseThrow(() -> new UsernameNotFoundException("No enabled local user"));
	}

}
