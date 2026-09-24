package com.example.commons.accounts;

import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authorization.LocalAuthorityLookup;

/**
 * Grants each enabled local user a {@code ROLE_} authority for every role of every group
 * the user belongs to.
 */
public class AppUserLocalAuthorityLookup implements LocalAuthorityLookup {

	private final AppUserRepository users;

	public AppUserLocalAuthorityLookup(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<Collection<GrantedAuthority>> findAuthorities(String username) {
		return this.users.findByUsernameAndEnabledTrue(username)
			.map(user -> user.getGroups()
				.stream()
				.flatMap(group -> group.getRoles().stream())
				.<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
				.distinct()
				.toList());
	}

}
