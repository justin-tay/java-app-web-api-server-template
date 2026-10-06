package com.example.commons.accounts;

import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.commons.accounts.domain.AccountStatus;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authorization.LocalAuthorityLookup;

/**
 * Grants each active local user an authority for every permission of every role the user
 * holds, named {@code domain:action} with no prefix (see docs/adr/0038).
 */
public class AppUserLocalAuthorityLookup implements LocalAuthorityLookup {

	private final AppUserRepository users;

	public AppUserLocalAuthorityLookup(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<Collection<GrantedAuthority>> findAuthorities(String username) {
		return this.users.findByUsernameAndStatus(username, AccountStatus.ACTIVE)
			.map(user -> user.permissions()
				.stream()
				.<GrantedAuthority>map(permission -> new SimpleGrantedAuthority(permission.getName()))
				.distinct()
				.toList());
	}

}
