package com.example.app.web.server.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * created, read, updated, listed, and deleted.
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

	@Autowired
	private MockMvc mockMvc;

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
				.content("{\"username\":\"new-user\",\"displayName\":\"New User\",\"enabled\":true,\"groupIds\":[\""
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
		this.mockMvc.perform(get("/admin/roles/missing").with(as("ROLE_MANAGE")))
			.andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.type").value("urn:problem:resource-not-found"))
			.andExpect(jsonPath("$.detail").value("Role was not found."));
		this.mockMvc.perform(get("/admin/groups/missing").with(as("GROUP_MANAGE")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Group was not found."));
		this.mockMvc.perform(get("/admin/users/missing").with(as("USER_MANAGE")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("User was not found."));
	}

	@Test
	void duplicateNameReturnsConflictProblemDetail() throws Exception {
		this.mockMvc
			.perform(post("/admin/groups").with(as("GROUP_MANAGE"))
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
		this.mockMvc.perform(delete("/admin/groups/" + ADMINISTRATORS_GROUP_ID).with(as("GROUP_MANAGE")).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Group contains users."));
		this.mockMvc.perform(delete("/admin/roles/" + USER_MANAGE_ROLE_ID).with(as("ROLE_MANAGE")).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Role is assigned to a group."));
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
	@Transactional
	void roleLifecycle() throws Exception {
		String location = this.mockMvc
			.perform(post("/admin/roles").with(as("ROLE_MANAGE"))
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
			.perform(put(location).with(as("ROLE_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"REPORT_EXPORT\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("REPORT_EXPORT"));
		this.mockMvc.perform(get(location).with(as("ROLE_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("REPORT_EXPORT"));

		this.mockMvc.perform(delete(location).with(as("ROLE_MANAGE")).with(csrf())).andExpect(status().isNoContent());
		this.mockMvc.perform(get(location).with(as("ROLE_MANAGE"))).andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void groupLifecycle() throws Exception {
		String location = this.mockMvc
			.perform(post("/admin/groups").with(as("GROUP_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Viewers\",\"roleIds\":[\"" + APPLICATION_USER_ROLE_ID + "\"]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roles[0].name").value("APPLICATION_USER"))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith("/admin/groups/");

		this.mockMvc.perform(get("/admin/groups").param("roleId", APPLICATION_USER_ROLE_ID).with(as("GROUP_MANAGE")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(hasItem("Report Viewers")));

		this.mockMvc
			.perform(put(location).with(as("GROUP_MANAGE"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Readers\",\"roleIds\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Report Readers"))
			.andExpect(jsonPath("$.roles").isEmpty());

		this.mockMvc.perform(delete(location).with(as("GROUP_MANAGE")).with(csrf())).andExpect(status().isNoContent());
		this.mockMvc.perform(get(location).with(as("GROUP_MANAGE"))).andExpect(status().isNotFound());
	}

	/**
	 * Authenticates as a user holding one local role, with the authority
	 * {@code AppUserLocalAuthorityLookup} grants for it. The test user builder's
	 * {@code roles()} cannot be used, because it rejects {@code ROLE_MANAGE} for starting
	 * with {@code ROLE_}.
	 */
	private static RequestPostProcessor as(String role) {
		return user("administrator").authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

}
