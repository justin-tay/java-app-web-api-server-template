package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;

class AppUserLocalAuthorityLookupTest {

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final AppUserLocalAuthorityLookup lookup = new AppUserLocalAuthorityLookup(this.users);

	@Test
	void grantsARoleAuthorityForEveryRoleOfEveryGroupOnce() {
		AppRole userManage = new AppRole("USER_MANAGE");
		AppGroup managers = new AppGroup("managers");
		managers.getRoles().add(userManage);
		AppGroup administrators = new AppGroup("administrators");
		administrators.getRoles().add(userManage);
		administrators.getRoles().add(new AppRole("ROLE_MANAGE"));
		AppUser alice = new AppUser("alice", "Alice", "alice@example.com", true);
		alice.getGroups().add(managers);
		alice.getGroups().add(administrators);
		when(this.users.findByUsernameAndEnabledTrue("alice")).thenReturn(Optional.of(alice));

		assertThat(this.lookup.findAuthorities("alice"))
			.hasValueSatisfying(authorities -> assertThat(authorities).extracting(GrantedAuthority::getAuthority)
				.containsExactlyInAnyOrder("ROLE_USER_MANAGE", "ROLE_ROLE_MANAGE"));
	}

	@Test
	void findsNothingForAMissingOrDisabledUser() {
		when(this.users.findByUsernameAndEnabledTrue("mallory")).thenReturn(Optional.empty());

		assertThat(this.lookup.findAuthorities("mallory")).isEmpty();
	}

}
