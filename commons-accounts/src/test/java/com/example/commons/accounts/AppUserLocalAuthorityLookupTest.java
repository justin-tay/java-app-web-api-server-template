package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.core.GrantedAuthority;

import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;

@AccountsJpaTest
class AppUserLocalAuthorityLookupTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Test
	void grantsARoleAuthorityForEveryRoleOfEveryGroupOnce() {
		AppRole userManage = this.entityManager.persist(new AppRole("USER_MANAGE"));
		AppRole roleManage = this.entityManager.persist(new AppRole("ROLE_MANAGE"));
		AppGroup managers = new AppGroup("managers");
		managers.getRoles().add(userManage);
		AppGroup administrators = new AppGroup("administrators");
		administrators.getRoles().add(userManage);
		administrators.getRoles().add(roleManage);
		AppUser alice = new AppUser("alice", "Alice", "alice@example.com", true);
		alice.getGroups().add(this.entityManager.persist(managers));
		alice.getGroups().add(this.entityManager.persist(administrators));
		this.entityManager.persist(alice);
		this.entityManager.flush();
		this.entityManager.clear();

		assertThat(lookup().findAuthorities("alice"))
			.hasValueSatisfying((authorities) -> assertThat(authorities).extracting(GrantedAuthority::getAuthority)
				.containsExactlyInAnyOrder("ROLE_USER_MANAGE", "ROLE_ROLE_MANAGE"));
	}

	@Test
	void findsNothingForADisabledUser() {
		AppUser mallory = new AppUser("mallory", "Mallory", null, false);
		mallory.getGroups().add(this.entityManager.persist(new AppGroup("users")));
		this.entityManager.persist(mallory);

		assertThat(lookup().findAuthorities("mallory")).isEmpty();
	}

	@Test
	void findsNothingForAMissingUser() {
		assertThat(lookup().findAuthorities("nobody")).isEmpty();
	}

	private AppUserLocalAuthorityLookup lookup() {
		return new AppUserLocalAuthorityLookup(this.users);
	}

}
