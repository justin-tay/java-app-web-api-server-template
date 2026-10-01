package com.example.commons.accounts.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.session.SessionRegistryImpl;

import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.admin.AdminDtos.GroupResponse;
import com.example.commons.accounts.admin.AdminDtos.RoleSummary;
import com.example.commons.accounts.admin.AdminDtos.Summary;
import com.example.commons.accounts.admin.AdminDtos.UserResponse;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Tests that {@link GroupAdminController} returns the roles of a group sorted by display
 * name then name, and {@link UserAdminController} returns the groups of a user sorted by
 * name, each ignoring case. The entities hold these in hash sets ordered by object
 * identity, so every read reloads them in a cleared persistence context to get new
 * instances, as each request does.
 */
@AccountsJpaTest
class AdminResponseOrderTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppGroupRepository groups;

	@Autowired
	private AppRoleRepository roles;

	private GroupAdminController groupController;

	private UserAdminController userController;

	@BeforeEach
	void setUp() {
		SessionRevocationService revocation = new SessionRevocationService(new SessionRegistryImpl(), null,
				new SessionLifecycleAuditLogger());
		AccountAuditLogger auditLogger = new AccountAuditLogger();
		AdministrationService service = new AdministrationService(this.users, this.groups, this.roles, revocation,
				auditLogger);
		this.groupController = new GroupAdminController(service);
		this.userController = new UserAdminController(service,
				new AccountLifecycleService(this.users, revocation, auditLogger, null, null, Clock.systemUTC()));
	}

	@Test
	void returnsTheRolesOfAGroupSortedByDisplayNameThenName() {
		AppGroup group = this.entityManager.persist(new AppGroup("Staff"));
		for (AppRole role : List.of(new AppRole("ZETA", "reports"), new AppRole("ALPHA", "Users"),
				new AppRole("MIKE", "admin"), new AppRole("BETA", "Users"), new AppRole("OMEGA", "Billing"))) {
			group.getRoles().add(this.entityManager.persist(role));
		}
		this.entityManager.flush();

		List<String> expected = List.of("MIKE", "OMEGA", "ZETA", "ALPHA", "BETA");
		for (int read = 0; read < 5; read++) {
			assertThat(roleNames(this.groupController.get(reload(group).getId()))).isEqualTo(expected);
		}
		this.entityManager.clear();
		assertThat(this.groupController.list(null, "Staff", null, 0, 20, new MockHttpServletRequest()).items())
			.singleElement()
			.satisfies((response) -> assertThat(roleNames(response)).isEqualTo(expected));
	}

	@Test
	void returnsTheGroupsOfAUserSortedByName() {
		AppUser user = new AppUser("sorted-user", "Sorted User", null);
		for (String name : List.of("zebras", "Mangoes", "apples", "Bananas")) {
			user.getGroups().add(this.entityManager.persist(new AppGroup(name)));
		}
		this.entityManager.persist(user);
		this.entityManager.flush();

		List<String> expected = List.of("apples", "Bananas", "Mangoes", "zebras");
		for (int read = 0; read < 5; read++) {
			this.entityManager.clear();
			assertThat(groupNames(this.userController.get(user.getId()))).isEqualTo(expected);
		}
		this.entityManager.clear();
		assertThat(this.userController
			.list(null, "sorted-user", null, null, null, null, null, null, null, 0, 20, new MockHttpServletRequest())
			.items()).singleElement().satisfies((response) -> assertThat(groupNames(response)).isEqualTo(expected));
	}

	private AppGroup reload(AppGroup group) {
		this.entityManager.clear();
		return this.entityManager.find(AppGroup.class, group.getId());
	}

	private List<String> roleNames(GroupResponse response) {
		return response.roles().stream().map(RoleSummary::name).toList();
	}

	private List<String> groupNames(UserResponse response) {
		return response.groups().stream().map(Summary::name).toList();
	}

}
