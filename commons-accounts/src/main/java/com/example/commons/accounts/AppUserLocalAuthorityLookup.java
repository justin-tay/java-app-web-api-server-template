package com.example.commons.accounts;

import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.authorization.RolePrefix;

/**
 * Grants each active local user a {@code ROLE_} authority for every role of every group
 * the user belongs to.
 */
public class AppUserLocalAuthorityLookup implements LocalAuthorityLookup {

	private final AppUserRepository users;

	public AppUserLocalAuthorityLookup(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<Collection<GrantedAuthority>> findAuthorities(String username) {
		return this.users.findByUsernameAndStatus(username, AccountStatus.ACTIVE)
			.map(user -> user.getGroups()
				.stream()
				.flatMap(group -> group.getRoles().stream())
				.<GrantedAuthority>map(role -> new SimpleGrantedAuthority(RolePrefix.VALUE + role.getName()))
				.distinct()
				.toList());
	}

}
