package com.example.app.web.server.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests the commons-accounts administration API as this application serves it, against
 * the seeded test database: each API admits only its own management role, conflicts and
 * unknown resources are answered with Problem Details, and groups and roles can be
 * created, read, updated, listed, and deleted. Every change needs a recent OpenID Connect
 * login, whose roles the application reloads from the seeded user it names, and is kept
 * from granting more than that user holds.
 *
 * <p>
 * Tests that change data run in a transaction that is rolled back, so every other test
 * sees only the seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiIntegrationTest {

	private static final String USER_MANAGE_ROLE_ID = "00000000-0000-0000-0000-000000000001";

	private static final String APPLICATION_USER_ROLE_ID = "00000000-0000-0000-0000-000000000004";

	private static final String ADMINISTRATORS_GROUP_ID = "00000000-0000-0000-0000-000000000011";

	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-00000000ffff";

	private static final String TEST_USERS_GROUP_ID = "00000000-0000-0000-0000-000000000012";

	private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000021";

	private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000022";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	/**
	 * Each API admits its own management role and rejects the others. The role API is the
	 * case to watch: {@code ROLE_MANAGE} becomes the authority {@code ROLE_ROLE_MANAGE},
	 * which both the application's URL rule and {@code RoleAdminController}'s
	 * {@code @PreAuthorize} check with {@code hasAuthority}.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			/admin/users  | USER_MANAGE      | 200
			/admin/users  | GROUP_MANAGE     | 403
			/admin/users  | ROLE_MANAGE      | 403
			/admin/groups | GROUP_MANAGE     | 200
			/admin/groups | USER_MANAGE      | 403
			/admin/groups | ROLE_MANAGE      | 403
			/admin/roles  | ROLE_MANAGE      | 200
			/admin/roles  | USER_MANAGE      | 403
			/admin/roles  | GROUP_MANAGE     | 403
			/admin/roles  | APPLICATION_USER | 403
			""")
	void eachApiAdmitsOnlyItsOwnManagementRole(String path, String role, int expectedStatus) throws Exception {
		this.mockMvc.perform(get(path).with(as(role))).andExpect(status().is(expectedStatus));
	}

	@Test
	@Transactional
	void writesAreRestrictedToTheManagementRoleToo() throws Exception {
		this.mockMvc
			.perform(post("/admin/users").with(as("GROUP_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"new-user\",\"name\":\"New User\",\"enabled\":true,\"groupIds\":[\""
						+ ADMINISTRATORS_GROUP_ID + "\"]}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/admin/roles").with(as("GROUP_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"REPORT_VIEW\"}"))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(delete("/admin/groups/" + ADMINISTRATORS_GROUP_ID).with(as("ROLE_MANAGE")).with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	void unknownResourceReturnsNotFoundProblemDetail() throws Exception {
		this.mockMvc.perform(get("/admin/roles/" + UNKNOWN_ID).with(as("ROLE_MANAGE")))
			.andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.type").value("urn:problem:resource-not-found"))
			.andExpect(jsonPath("$.detail").value("Role was not found."));
		this.mockMvc.perform(get("/admin/groups/" + UNKNOWN_ID).with(as("GROUP_MANAGE")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Group was not found."));
		this.mockMvc.perform(get("/admin/users/" + UNKNOWN_ID).with(as("USER_MANAGE")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("User was not found."));
	}

	@Test
	void malformedIdsAreRejectedBeforeAnyLookup() throws Exception {
		this.mockMvc.perform(get("/admin/roles/missing").with(as("ROLE_MANAGE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:problem:validation-failed"));
		this.mockMvc.perform(get("/admin/users").param("groupId", "' or 1=1 --").with(as("USER_MANAGE")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateNameReturnsConflictProblemDetail() throws Exception {
		this.mockMvc
			.perform(post("/admin/groups").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Administrators\"}"))
			.andExpect(status().isConflict())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.type").value("urn:problem:resource-conflict"))
			.andExpect(jsonPath("$.detail").value("Group name already exists."));
	}

	@Test
	void groupsAndRolesInUseCannotBeDeleted() throws Exception {
		this.mockMvc.perform(delete("/admin/groups/" + ADMINISTRATORS_GROUP_ID).with(recentAdmin()).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Group contains users."));
		this.mockMvc.perform(delete("/admin/roles/" + APPLICATION_USER_ROLE_ID).with(recentAdmin()).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Role is assigned to a group."));
	}

	/**
	 * The seeded {@code admin} holds the three management roles but not
	 * {@code APPLICATION_USER}, so it can grant only those, cannot change its own access,
	 * and cannot delete a reserved role.
	 */
	@Test
	@Transactional
	void anAdministratorCannotGrantMoreThanTheyHold() throws Exception {
		this.mockMvc
			.perform(post("/admin/users").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"new-user\",\"name\":\"New User\",\"enabled\":true,\"groupIds\":[\""
						+ TEST_USERS_GROUP_ID + "\"]}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:problem:access-denied"));
		this.mockMvc
			.perform(put("/admin/groups/" + ADMINISTRATORS_GROUP_ID).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Administrators\",\"roleIds\":[\"" + USER_MANAGE_ROLE_ID + "\",\""
						+ APPLICATION_USER_ROLE_ID + "\"]}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(put("/admin/users/" + ADMIN_USER_ID).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Administrator\",\"enabled\":false,\"groupIds\":[\"" + ADMINISTRATORS_GROUP_ID
						+ "\"]}"))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(delete("/admin/roles/" + USER_MANAGE_ROLE_ID).with(recentAdmin()).with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	@Transactional
	void aChangeNeedsARecentLoginButAReadDoesNot() throws Exception {
		Instant longAgo = Instant.now().minus(Duration.ofHours(1));
		this.mockMvc
			.perform(post("/admin/roles").with(admin(longAgo))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"REPORT_VIEW\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:problem:reauthentication-required"))
			.andExpect(jsonPath("$.max_age").value(900));
		this.mockMvc
			.perform(post("/admin/roles").with(as("ROLE_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"REPORT_VIEW\"}"))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/admin/roles").with(admin(longAgo))).andExpect(status().isOk());
	}

	@Test
	@Transactional
	void endingSessionsDoesNotNeedARecentLogin() throws Exception {
		Instant longAgo = Instant.now().minus(Duration.ofHours(1));
		this.mockMvc.perform(delete("/admin/users/" + TEST_USER_ID + "/sessions").with(admin(longAgo)).with(csrf()))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(delete("/admin/users/sessions").with(admin(longAgo)).with(csrf()))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(delete("/admin/users/sessions").with(as("GROUP_MANAGE")).with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	void listsFilterByMembershipAndRole() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("groupId", ADMINISTRATORS_GROUP_ID).with(as("USER_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("admin", "multi-group-user")));
		this.mockMvc.perform(get("/admin/groups").param("roleId", USER_MANAGE_ROLE_ID).with(as("GROUP_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("Administrators")));
		this.mockMvc.perform(get("/admin/roles").param("name", "manage").with(as("ROLE_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name")
				.value(containsInAnyOrder("USER_MANAGE", "GROUP_MANAGE", "ROLE_MANAGE")));
	}

	@Test
	void searchMatchesAnyUserFieldOrExactId() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("search", "GROUP").with(as("USER_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("multi-group-user")));
		this.mockMvc.perform(get("/admin/users").param("search", "Test User").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("test-user")));
		this.mockMvc.perform(get("/admin/users").param("search", TEST_USER_ID).with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("test-user")));
		this.mockMvc.perform(get("/admin/users").param("search", "%").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/groups").param("search", "admin").with(as("GROUP_MANAGE")))
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("Administrators")));
		this.mockMvc.perform(get("/admin/roles").param("search", "user_").with(as("ROLE_MANAGE")))
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("USER_MANAGE")));
	}

	@Test
	void usersFilterByCreatedDateRangeInclusively() throws Exception {
		this.mockMvc
			.perform(get("/admin/users").param("createdFrom", "2025-12-31")
				.param("createdTo", "2026-01-01")
				.with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(3));
		this.mockMvc.perform(get("/admin/users").param("createdFrom", "2026-01-02").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/users").param("createdTo", "2025-12-30").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/users").param("createdFrom", "yesterday").with(as("USER_MANAGE")))
			.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void statusIsPendingUntilTheFirstSignInThenActive() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("status", "pending").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.items[0].status").value("pending"))
			.andExpect(jsonPath("$.items[0].lastLoginAt").doesNotExist());
		this.mockMvc.perform(get("/admin/users").param("status", "active").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(0));

		this.eventPublisher.publishEvent(new InteractiveAuthenticationSuccessEvent(
				new TestingAuthenticationToken("test-user", "n/a"), getClass()));

		this.mockMvc.perform(get("/admin/users").param("status", "active").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("test-user")))
			.andExpect(jsonPath("$.items[0].lastLoginAt").exists());
		this.mockMvc.perform(get("/admin/users").param("status", "pending").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(2));
		this.mockMvc.perform(get("/admin/users").param("status", "disabled").with(as("USER_MANAGE")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/users").param("status", "unknown").with(as("USER_MANAGE")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/admin/users").param("sort", "lastLoginAt,desc").with(as("USER_MANAGE")))
			.andExpect(status().isOk());
	}

	@Test
	void sortAcceptsRepeatedParametersWithLimits() throws Exception {
		this.mockMvc
			.perform(get("/admin/users").param("sort", "createdAt,asc", "username,desc").with(as("USER_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].username").value("test-user"));
		this.mockMvc.perform(get("/admin/users").param("sort", "username", "username,desc").with(as("USER_MANAGE")))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/admin/users").param("sort", "username", "name", "createdAt", "updatedAt")
				.with(as("USER_MANAGE")))
			.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void roleLifecycle() throws Exception {
		String location = this.mockMvc
			.perform(post("/admin/roles").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"REPORT_VIEW\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("REPORT_VIEW"))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith("/admin/roles/");

		this.mockMvc
			.perform(put(location).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"REPORT_EXPORT\"}"))
			.andExpect(status().isMethodNotAllowed());
		this.mockMvc.perform(get(location).with(recentAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("REPORT_VIEW"));

		this.mockMvc.perform(delete(location).with(recentAdmin()).with(csrf())).andExpect(status().isNoContent());
		this.mockMvc.perform(get(location).with(recentAdmin())).andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void groupLifecycle() throws Exception {
		String location = this.mockMvc
			.perform(post("/admin/groups").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Viewers\",\"roleIds\":[\"" + USER_MANAGE_ROLE_ID + "\"]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roles[0].name").value("USER_MANAGE"))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith("/admin/groups/");

		this.mockMvc.perform(get("/admin/groups").param("roleId", USER_MANAGE_ROLE_ID).with(recentAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(hasItem("Report Viewers")));

		this.mockMvc
			.perform(put(location).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Readers\",\"roleIds\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Report Readers"))
			.andExpect(jsonPath("$.roles").isEmpty());

		this.mockMvc.perform(delete(location).with(recentAdmin()).with(csrf())).andExpect(status().isNoContent());
		this.mockMvc.perform(get(location).with(recentAdmin())).andExpect(status().isNotFound());
	}

	/**
	 * Authenticates as a user holding the given local roles, with the authorities
	 * {@code AppUserLocalAuthorityLookup} grants for them. The test user builder's
	 * {@code roles()} cannot be used, because it rejects {@code ROLE_MANAGE} for starting
	 * with {@code ROLE_}.
	 */
	/**
	 * Logs in through OpenID Connect as the seeded {@code admin}, just now.
	 */
	private static RequestPostProcessor recentAdmin() {
		return admin(Instant.now());
	}

	/**
	 * Logs in through OpenID Connect as the seeded {@code admin} at the given time.
	 * {@code LocalAuthorityRefreshFilter} replaces the login's authorities with the
	 * administrator's local roles.
	 */
	private static RequestPostProcessor admin(Instant authTime) {
		return oidcLoginAs("admin", authTime);
	}

	private static RequestPostProcessor as(String... roles) {
		return user("administrator")
			.authorities(Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
	}

}
