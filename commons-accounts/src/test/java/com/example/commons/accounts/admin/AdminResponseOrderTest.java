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

import com.example.commons.accounts.audit.AccountAudit;
import com.example.commons.audit.AuditTrail;
import com.example.commons.accounts.AccountsJpaTest;
import com.example.commons.accounts.Permissions;
import com.example.commons.accounts.admin.AdminDtos.PermissionSummary;
import com.example.commons.accounts.admin.AdminDtos.RoleResponse;
import com.example.commons.accounts.admin.AdminDtos.Summary;
import com.example.commons.accounts.admin.AdminDtos.UserResponse;
import com.example.commons.accounts.domain.AppPermission;
import com.example.commons.accounts.domain.AppPermissionRepository;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.session.SessionLifecycleAuditLogger;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Tests that {@link RoleAdminController} returns the permissions of a role sorted by
 * name, and {@link UserAdminController} returns the roles of a user sorted by name, each
 * ignoring case. The entities hold these in hash sets ordered by object identity, so
 * every read reloads them in a cleared persistence context to get new instances, as each
 * request does.
 */
@AccountsJpaTest
class AdminResponseOrderTest {

	@Autowired
	private AuditTrail auditTrail;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AppRoleRepository roles;

	@Autowired
	private AppPermissionRepository permissions;

	private RoleAdminController roleController;

	private UserAdminController userController;

	@BeforeEach
	void setUp() {
		SessionRevocationService revocation = new SessionRevocationService(new SessionRegistryImpl(), null,
				new SessionLifecycleAuditLogger());
		AccountAudit audit = new AccountAudit(this.auditTrail);
		AdministrationService service = new AdministrationService(this.users, this.roles, this.permissions, revocation,
				audit);
		this.roleController = new RoleAdminController(service);
		this.userController = new UserAdminController(service,
				new AccountLifecycleService(this.users, revocation, audit, null, null, Clock.systemUTC()));
	}

	@Test
	void returnsThePermissionsOfARoleSortedByName() {
		AppRole role = this.entityManager.persist(new AppRole("Staff"));
		for (String name : List.of(Permissions.USER_UPDATE, Permissions.AUDIT_READ, Permissions.USER_READ,
				Permissions.APPLICATION_ACCESS, Permissions.ROLE_CREATE)) {
			role.getPermissions().add(permission(name));
		}
		this.entityManager.flush();

		List<String> expected = List.of("application:access", "audit:read", "role:create", "user:read", "user:update");
		for (int read = 0; read < 5; read++) {
			assertThat(permissionNames(this.roleController.get(reload(role).getPublicId()))).isEqualTo(expected);
		}
		this.entityManager.clear();
		assertThat(this.roleController.list(null, "Staff", null, 0, 20, new MockHttpServletRequest()).items())
			.singleElement()
			.satisfies((response) -> assertThat(permissionNames(response)).isEqualTo(expected));
	}

	@Test
	void returnsTheRolesOfAUserSortedByName() {
		AppUser user = new AppUser("sorted-user", "Sorted User", null);
		for (String name : List.of("zebras", "Mangoes", "apples", "Bananas")) {
			user.getRoles().add(this.entityManager.persist(new AppRole(name)));
		}
		this.entityManager.persist(user);
		this.entityManager.flush();

		List<String> expected = List.of("apples", "Bananas", "Mangoes", "zebras");
		for (int read = 0; read < 5; read++) {
			this.entityManager.clear();
			assertThat(roleNames(this.userController.get(user.getPublicId()))).isEqualTo(expected);
		}
		this.entityManager.clear();
		assertThat(this.userController
			.list(null, "sorted-user", null, null, null, null, null, null, null, null, null, 0, 20,
					new MockHttpServletRequest())
			.items()).singleElement().satisfies((response) -> assertThat(roleNames(response)).isEqualTo(expected));
	}

	private AppPermission permission(String name) {
		return this.permissions.findAll()
			.stream()
			.filter(permission -> permission.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private AppRole reload(AppRole role) {
		this.entityManager.clear();
		return this.entityManager.find(AppRole.class, role.getId());
	}

	private List<String> permissionNames(RoleResponse response) {
		return response.permissions().stream().map(PermissionSummary::name).toList();
	}

	private List<String> roleNames(UserResponse response) {
		return response.roles().stream().map(Summary::name).toList();
	}

}
