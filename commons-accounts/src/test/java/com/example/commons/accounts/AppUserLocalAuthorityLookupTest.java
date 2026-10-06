package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.core.GrantedAuthority;

import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppPermissionRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.ReasonCode;

@AccountsJpaTest
class AppUserLocalAuthorityLookupTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppPermissionRepository permissions;

	@Test
	void grantsAnAuthorityForEveryPermissionOfEveryRoleOnce() {
		AppRole managers = new AppRole("managers");
		managers.getPermissions().add(permission(Permissions.USER_READ));
		AppRole administrators = new AppRole("administrators");
		administrators.getPermissions().add(permission(Permissions.USER_READ));
		administrators.getPermissions().add(permission(Permissions.ROLE_ADD_PERMISSION));
		AppUser alice = new AppUser("alice", "Alice", "alice@example.com");
		alice.getRoles().add(this.entityManager.persist(managers));
		alice.getRoles().add(this.entityManager.persist(administrators));
		this.entityManager.persist(alice);
		this.entityManager.flush();
		this.entityManager.clear();

		assertThat(lookup().findAuthorities("alice"))
			.hasValueSatisfying((authorities) -> assertThat(authorities).extracting(GrantedAuthority::getAuthority)
				.containsExactlyInAnyOrder("user:read", "role:add-permission"));
	}

	@Test
	void findsNothingForASuspendedUser() {
		AppUser mallory = new AppUser("mallory", "Mallory", null);
		mallory.suspend(Instant.now(), ReasonCode.OTHER, null);
		mallory.getRoles().add(this.entityManager.persist(new AppRole("users")));
		this.entityManager.persist(mallory);

		assertThat(lookup().findAuthorities("mallory")).isEmpty();
	}

	@Test
	void findsNothingForAMissingUser() {
		assertThat(lookup().findAuthorities("nobody")).isEmpty();
	}

	private AppPermission permission(String name) {
		return this.permissions.findAll()
			.stream()
			.filter(permission -> permission.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private AppUserLocalAuthorityLookup lookup() {
		return new AppUserLocalAuthorityLookup(this.users);
	}

}
