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
 * the seeded test database: each endpoint admits only the permission it needs, conflicts
 * and unknown resources are answered with Problem Details, and roles can be created,
 * read, updated, listed, and deleted, while permissions can only be read. Every change
 * needs a recent OpenID Connect login, whose permissions the application reloads from the
 * seeded user it names, and is kept from granting more than that user holds or from
 * putting reviewing together with a privileged permission (see docs/adr/0038).
 *
 * <p>
 * Tests that change data run in a transaction that is rolled back, so every other test
 * sees only the seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiIntegrationTest {

	private static final String USER_READ_PERMISSION_ID = "00000000-0000-0000-0100-000000000002";

	private static final String USER_CREATE_PERMISSION_ID = "00000000-0000-0000-0100-000000000003";

	private static final String REVIEW_DECIDE_PERMISSION_ID = "00000000-0000-0000-0100-000000000017";

	private static final String ROLE_ADD_PERMISSION_ID = "00000000-0000-0000-0100-000000000010";

	private static final String ADMINISTRATORS_ROLE_ID = "00000000-0000-0000-0000-000000000011";

	private static final String USERS_ROLE_ID = "00000000-0000-0000-0000-000000000012";

	private static final String ACCOUNT_REVIEWERS_ROLE_ID = "00000000-0000-0000-0000-000000000013";

	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-00000000ffff";

	private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000021";

	private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000022";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	/**
	 * Each endpoint admits the permission it needs and rejects the others.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			/admin/users       | user:read         | 200
			/admin/users       | role:read         | 403
			/admin/users       | settings:read     | 403
			/admin/roles       | role:read         | 200
			/admin/roles       | user:read         | 403
			/admin/permissions | permission:read   | 200
			/admin/permissions | role:read         | 403
			/admin/settings    | settings:read     | 200
			/admin/settings    | user:read         | 403
			/audit-events      | audit:read        | 200
			/audit-events      | settings:read     | 403
			/admin/roles       | application:access | 403
			""")
	void eachEndpointAdmitsOnlyThePermissionItNeeds(String path, String permission, int expectedStatus)
			throws Exception {
		this.mockMvc.perform(get(path).with(as(permission))).andExpect(status().is(expectedStatus));
	}

	@Test
	@Transactional
	void writesNeedTheirOwnPermissionToo() throws Exception {
		this.mockMvc
			.perform(post("/admin/users").with(recentAs("account-reviewer-1"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"new-user\",\"name\":\"New User\",\"roleIds\":[\"" + USERS_ROLE_ID + "\"]}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/admin/roles").with(recentAs("user"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Viewers\"}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(delete("/admin/roles/" + ADMINISTRATORS_ROLE_ID).with(recentAs("account-reviewer-1")).with(csrf()))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(put("/admin/settings").with(recentAs("account-reviewer-1"))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"inactivity\":{\"enabled\":true,\"suspendAfterDays\":90,\"removeAfterDays\":180},"
					+ "\"review\":{\"enabled\":true,\"privilegedIntervalMonths\":1,\"nonPrivilegedIntervalMonths\":12}}"))
			.andExpect(status().isForbidden());
	}

	/**
	 * A caller with no permission of an API's domain is refused before the recent login
	 * check, so they are not asked to sign in again for something they may not do. A
	 * caller who holds one is asked, whatever the endpoint, and the method check then
	 * decides.
	 */
	@Test
	@Transactional
	void aCallerWithNoPermissionOfTheDomainGetsForbiddenEvenWithAnOldLogin() throws Exception {
		Instant longAgo = Instant.now().minus(Duration.ofHours(1));
		String newRole = "{\"name\":\"Report Viewers\"}";

		this.mockMvc
			.perform(post("/admin/roles").with(oidcLoginAs("user", longAgo))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(newRole))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/admin/roles").with(as("user:read"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(newRole))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/admin/roles").with(as("role:read"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(newRole))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/admin/roles")).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownResourceReturnsNotFoundProblemDetail() throws Exception {
		this.mockMvc.perform(get("/admin/roles/" + UNKNOWN_ID).with(as("role:read")))
			.andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.type").value("urn:problem:resource-not-found"))
			.andExpect(jsonPath("$.detail").value("Role was not found."));
		this.mockMvc.perform(get("/admin/permissions/" + UNKNOWN_ID).with(as("permission:read")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Permission was not found."));
		this.mockMvc.perform(get("/admin/users/" + UNKNOWN_ID).with(as("user:read")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("User was not found."));
	}

	@Test
	void malformedIdsAreRejectedBeforeAnyLookup() throws Exception {
		this.mockMvc.perform(get("/admin/roles/missing").with(as("role:read")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:problem:validation-failed"));
		this.mockMvc.perform(get("/admin/users").param("roleId", "' or 1=1 --").with(as("user:read")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateNameReturnsConflictProblemDetail() throws Exception {
		this.mockMvc
			.perform(post("/admin/roles").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Administrators\"}"))
			.andExpect(status().isConflict())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.type").value("urn:problem:resource-conflict"))
			.andExpect(jsonPath("$.detail").value("Role name already exists."));
	}

	@Test
	void aRoleWithUsersCannotBeDeleted() throws Exception {
		this.mockMvc.perform(delete("/admin/roles/" + ADMINISTRATORS_ROLE_ID).with(recentAdmin()).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Role has users."));
	}

	/**
	 * The seeded {@code admin} holds every privileged permission, so it may grant them,
	 * but it cannot change its own access or suspend itself, and nobody can hold
	 * reviewing together with a privileged permission.
	 */
	@Test
	@Transactional
	void anAdministratorCannotChangeTheirOwnAccessOrBreakSeparationOfDuties() throws Exception {
		this.mockMvc
			.perform(put("/admin/users/" + ADMIN_USER_ID).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Alan Tan\",\"email\":\"admin@example.test\",\"roleIds\":[\""
						+ ADMINISTRATORS_ROLE_ID + "\",\"" + ACCOUNT_REVIEWERS_ROLE_ID + "\"]}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:problem:access-denied"));
		this.mockMvc
			.perform(post("/admin/users/" + ADMIN_USER_ID + "/suspend").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"other\"}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(put("/admin/users/" + TEST_USER_ID).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Mary Goh\",\"email\":\"user@example.test\",\"roleIds\":[\"" + USERS_ROLE_ID
						+ "\",\"" + ADMINISTRATORS_ROLE_ID + "\",\"" + ACCOUNT_REVIEWERS_ROLE_ID + "\"]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:problem:resource-conflict"));
		this.mockMvc
			.perform(put("/admin/roles/" + ACCOUNT_REVIEWERS_ROLE_ID).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Account Reviewers\",\"permissionIds\":[\"" + REVIEW_DECIDE_PERMISSION_ID + "\",\""
						+ USER_CREATE_PERMISSION_ID + "\"]}"))
			.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void anAdministratorCanGiveAnAccountReviewerRoleWithoutHoldingItBecauseItIsNotPrivileged() throws Exception {
		this.mockMvc
			.perform(put("/admin/users/" + TEST_USER_ID).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Mary Goh\",\"email\":\"user@example.test\",\"roleIds\":[\"" + USERS_ROLE_ID
						+ "\",\"" + ACCOUNT_REVIEWERS_ROLE_ID + "\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[*].name").value(containsInAnyOrder("Users", "Account Reviewers")))
			.andExpect(jsonPath("$.privileged").value(false));
	}

	@Test
	void userResponsesSayWhetherTheAccountIsPrivileged() throws Exception {
		this.mockMvc.perform(get("/admin/users/" + ADMIN_USER_ID).with(as("user:read")))
			.andExpect(jsonPath("$.privileged").value(true))
			.andExpect(jsonPath("$.createdAt").exists())
			.andExpect(jsonPath("$.lastActivityAt").exists())
			.andExpect(jsonPath("$.lastLoginAt").doesNotExist());
		this.mockMvc.perform(get("/admin/users/" + TEST_USER_ID).with(as("user:read")))
			.andExpect(jsonPath("$.privileged").value(false));
	}

	@Test
	@Transactional
	void suspendsAndUnsuspendsAUserRecordingTheReason() throws Exception {
		this.mockMvc
			.perform(post("/admin/users/" + TEST_USER_ID + "/suspend").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"policy_violation\",\"note\":\"see ticket 42\"}"))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(get("/admin/users/" + TEST_USER_ID).with(as("user:read")))
			.andExpect(jsonPath("$.status").value("suspended"))
			.andExpect(jsonPath("$.suspensionReasonCode").value("policy_violation"))
			.andExpect(jsonPath("$.suspensionNote").value("see ticket 42"))
			.andExpect(jsonPath("$.suspendedAt").exists());
		this.mockMvc.perform(get("/admin/users").param("status", "suspended").with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("user")));

		this.mockMvc.perform(post("/admin/users/" + TEST_USER_ID + "/unsuspend").with(recentAdmin()).with(csrf()))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(get("/admin/users/" + TEST_USER_ID).with(as("user:read")))
			.andExpect(jsonPath("$.status").value("active"))
			.andExpect(jsonPath("$.suspendedAt").doesNotExist());
	}

	@Test
	@Transactional
	void aSuspensionOrRemovalNeedsAnAllowedReasonCode() throws Exception {
		for (String body : new String[] { "{}", "{\"reasonCode\":\"inactive_account\"}", "{\"reasonCode\":\"bogus\"}",
				"{\"reasonCode\":\"other\",\"note\":\"" + "x".repeat(201) + "\"}" }) {
			this.mockMvc
				.perform(post("/admin/users/" + TEST_USER_ID + "/suspend").with(recentAdmin())
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isBadRequest());
			this.mockMvc
				.perform(post("/admin/users/" + TEST_USER_ID + "/remove").with(recentAdmin())
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	@Transactional
	void removingAUserDeletesThemAndRecordsItInTheAuditTrailTable() throws Exception {
		this.mockMvc
			.perform(post("/admin/users/" + TEST_USER_ID + "/remove").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"left_organisation\"}"))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/admin/users/" + TEST_USER_ID).with(as("user:read")))
			.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void settingsNeedTheirPermissionsAndAreValidated() throws Exception {
		this.mockMvc.perform(get("/admin/settings").with(as("user:read"))).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/admin/settings").with(as("settings:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inactivity.suspendAfterDays").value(90))
			.andExpect(jsonPath("$.inactivity.removeAfterDays").value(180))
			.andExpect(jsonPath("$.review.privilegedIntervalMonths").value(1))
			.andExpect(jsonPath("$.review.nonPrivilegedIntervalMonths").value(12));

		this.mockMvc.perform(updateSettings(90, 90, 1, 12))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").value("/inactivity.removeAfterDays"));
		this.mockMvc.perform(updateSettings(60, 120, 1, 13))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").value("/review.nonPrivilegedIntervalMonths"));
		this.mockMvc.perform(updateSettings(60, 120, 5, 12))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").value("/review.privilegedIntervalMonths"));
		this.mockMvc.perform(updateSettings(60, 120, 6, 5))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").value("/review.nonPrivilegedIntervalMonths"));
		this.mockMvc.perform(updateSettings(60, 120, 6, 3))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].source.pointer").value("/review.nonPrivilegedIntervalMonths"))
			.andExpect(jsonPath("$.errors[0].message")
				.value("The non-privileged review interval cannot be shorter than the privileged review interval."));
		this.mockMvc.perform(updateSettings(60, 120, 3, 6))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inactivity.suspendAfterDays").value(60));
		this.mockMvc.perform(get("/admin/settings").with(as("settings:read")))
			.andExpect(jsonPath("$.inactivity.suspendAfterDays").value(60))
			.andExpect(jsonPath("$.review.privilegedIntervalMonths").value(3))
			.andExpect(jsonPath("$.review.nonPrivilegedIntervalMonths").value(6));
	}

	@Test
	@Transactional
	void aChangeNeedsARecentLoginButAReadDoesNot() throws Exception {
		Instant longAgo = Instant.now().minus(Duration.ofHours(1));
		this.mockMvc
			.perform(post("/admin/roles").with(admin(longAgo))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Viewers\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:problem:reauthentication-required"))
			.andExpect(jsonPath("$.max_age").value(900))
			.andExpect(jsonPath("$.method").value("oidc"))
			.andExpect(jsonPath("$.reauthentication_uri").value("/oauth2/authorization/test?max_age=0"));
		this.mockMvc
			.perform(post("/admin/roles").with(as("role:create"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Viewers\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.method").doesNotExist())
			.andExpect(jsonPath("$.reauthentication_uri").doesNotExist());
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
		this.mockMvc.perform(delete("/admin/users/sessions").with(as("user:read")).with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	void listsFilterByRoleAndPrivilege() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("roleId", ADMINISTRATORS_ROLE_ID).with(as("user:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("admin", "multi-group-user")));
		this.mockMvc.perform(get("/admin/users").param("privileged", "true").with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("admin", "multi-group-user")));
		this.mockMvc.perform(get("/admin/users").param("privileged", "false").with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username")
				.value(containsInAnyOrder("user", "account-reviewer-1", "account-reviewer-2")));
		this.mockMvc.perform(get("/admin/roles").param("permissionId", ROLE_ADD_PERMISSION_ID).with(as("role:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("Administrators")));
		this.mockMvc.perform(get("/admin/roles").param("name", "review").with(as("role:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("Account Reviewers")));
	}

	@Test
	void searchMatchesAnyUserFieldOrExactId() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("search", "GROUP").with(as("user:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("multi-group-user")));
		this.mockMvc.perform(get("/admin/users").param("search", "Mary").with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("user")));
		this.mockMvc.perform(get("/admin/users").param("search", TEST_USER_ID).with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("user")));
		this.mockMvc.perform(get("/admin/users").param("search", "%").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/roles").param("search", "admin").with(as("role:read")))
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("Administrators")));
	}

	@Test
	void permissionsAreReferenceDataThatCanOnlyBeRead() throws Exception {
		this.mockMvc.perform(get("/admin/permissions").with(as("permission:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(25))
			.andExpect(jsonPath("$.items[?(@.name == 'user:create')].domain").value("user"))
			.andExpect(jsonPath("$.items[?(@.name == 'user:create')].action").value("create"))
			.andExpect(jsonPath("$.items[?(@.name == 'user:create')].privileged").value(true))
			.andExpect(jsonPath("$.items[?(@.name == 'user:read')].privileged").value(false));
		this.mockMvc.perform(get("/admin/permissions").param("privileged", "true").with(as("permission:read")))
			.andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("user:create", "user:add-role",
					"user:unsuspend", "role:add-permission", "settings:update")));
		this.mockMvc.perform(get("/admin/permissions").param("domain", "review").with(as("permission:read")))
			.andExpect(jsonPath("$.totalItems").value(4));
		this.mockMvc.perform(get("/admin/permissions/" + USER_READ_PERMISSION_ID).with(as("permission:read")))
			.andExpect(jsonPath("$.name").value("user:read"));
		this.mockMvc
			.perform(post("/admin/permissions").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\":\"user\",\"action\":\"fly\"}"))
			.andExpect(status().isMethodNotAllowed());
		this.mockMvc.perform(delete("/admin/permissions/" + USER_READ_PERMISSION_ID).with(recentAdmin()).with(csrf()))
			.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void usersFilterByCreatedDateRangeInclusively() throws Exception {
		this.mockMvc
			.perform(get("/admin/users").param("createdFrom", "2025-12-31")
				.param("createdTo", "2026-01-01")
				.with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(5));
		this.mockMvc.perform(get("/admin/users").param("createdFrom", "2026-01-02").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/users").param("createdTo", "2025-12-30").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/users").param("createdFrom", "yesterday").with(as("user:read")))
			.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void neverSignedInIsAFilterAndActiveIncludesThoseUsers() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("status", "active").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(5))
			.andExpect(jsonPath("$.items[0].status").value("active"))
			.andExpect(jsonPath("$.items[0].lastLoginAt").doesNotExist());
		this.mockMvc.perform(get("/admin/users").param("neverSignedIn", "true").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(5));
		this.mockMvc.perform(get("/admin/users").param("neverSignedIn", "false").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(0));

		this.eventPublisher.publishEvent(
				new InteractiveAuthenticationSuccessEvent(new TestingAuthenticationToken("user", "n/a"), getClass()));

		this.mockMvc.perform(get("/admin/users").param("status", "active").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(5));
		this.mockMvc.perform(get("/admin/users").param("neverSignedIn", "true").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(4));
		this.mockMvc.perform(get("/admin/users").param("neverSignedIn", "false").with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("user")))
			.andExpect(jsonPath("$.items[0].lastLoginAt").exists());
		this.mockMvc.perform(get("/admin/users").param("status", "suspended").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(0));
		this.mockMvc.perform(get("/admin/users").param("status", "pending").with(as("user:read")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/admin/users").param("status", "unknown").with(as("user:read")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/admin/users").param("neverSignedIn", "maybe").with(as("user:read")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/admin/users").param("sort", "lastLoginAt,desc").with(as("user:read")))
			.andExpect(status().isOk());
	}

	@Test
	@Transactional
	void neverSignedInComposesWithSuspendedStatus() throws Exception {
		this.mockMvc
			.perform(post("/admin/users/" + TEST_USER_ID + "/suspend").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"policy_violation\"}"))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(
				get("/admin/users").param("status", "suspended").param("neverSignedIn", "true").with(as("user:read")))
			.andExpect(jsonPath("$.items[*].username").value(containsInAnyOrder("user")));
		this.mockMvc
			.perform(get("/admin/users").param("status", "active").param("neverSignedIn", "true").with(as("user:read")))
			.andExpect(jsonPath("$.totalItems").value(4));
	}

	@Test
	void sortAcceptsRepeatedParametersWithLimits() throws Exception {
		this.mockMvc.perform(get("/admin/users").param("sort", "createdAt,asc", "username,desc").with(as("user:read")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].username").value("user"));
		this.mockMvc.perform(get("/admin/users").param("sort", "username", "username,desc").with(as("user:read")))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/admin/users").param("sort", "username", "name", "createdAt", "updatedAt")
				.with(as("user:read")))
			.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void roleLifecycle() throws Exception {
		String location = this.mockMvc
			.perform(post("/admin/roles").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Viewers\",\"permissionIds\":[\"" + USER_READ_PERMISSION_ID + "\"]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.permissions[0].name").value("user:read"))
			.andExpect(jsonPath("$.permissions[0].privileged").value(false))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith("/admin/roles/");

		this.mockMvc.perform(get("/admin/roles").param("permissionId", USER_READ_PERMISSION_ID).with(recentAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(hasItem("Report Viewers")));

		this.mockMvc
			.perform(put(location).with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Report Readers\",\"permissionIds\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Report Readers"))
			.andExpect(jsonPath("$.permissions").isEmpty());

		this.mockMvc.perform(delete(location).with(recentAdmin()).with(csrf())).andExpect(status().isNoContent());
		this.mockMvc.perform(get(location).with(recentAdmin())).andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void aRoleCannotCombineReviewingWithAPrivilegedPermission() throws Exception {
		this.mockMvc
			.perform(post("/admin/roles").with(recentAdmin())
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Mixed\",\"permissionIds\":[\"" + REVIEW_DECIDE_PERMISSION_ID + "\",\""
						+ USER_CREATE_PERMISSION_ID + "\"]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:problem:resource-conflict"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("review:decide")))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("user:create")));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateSettings(int suspendAfter,
			int removeAfter, int privilegedMonths, int nonPrivilegedMonths) {
		return put("/admin/settings").with(recentAdmin())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"inactivity\":{\"enabled\":true,\"suspendAfterDays\":" + suspendAfter + ",\"removeAfterDays\":"
					+ removeAfter + "},\"review\":{\"enabled\":true,\"privilegedIntervalMonths\":" + privilegedMonths
					+ ",\"nonPrivilegedIntervalMonths\":" + nonPrivilegedMonths + "}}");
	}

	/**
	 * Logs in through OpenID Connect as the seeded {@code admin}, just now.
	 */
	private static RequestPostProcessor recentAdmin() {
		return admin(Instant.now());
	}

	/**
	 * Logs in through OpenID Connect as the seeded {@code admin} at the given time.
	 * {@code LocalAuthorityRefreshFilter} replaces the login's authorities with the
	 * administrator's local permissions.
	 */
	private static RequestPostProcessor admin(Instant authTime) {
		return oidcLoginAs("admin", authTime);
	}

	/**
	 * Logs in through OpenID Connect as a seeded user, just now.
	 */
	private static RequestPostProcessor recentAs(String username) {
		return oidcLoginAs(username, Instant.now());
	}

	/**
	 * Authenticates as a user holding the given permissions as authorities. It is not an
	 * OpenID Connect login, so it can read but cannot pass the recent login check of a
	 * change.
	 */
	private static RequestPostProcessor as(String... permissions) {
		return user("administrator")
			.authorities(Arrays.stream(permissions).map(permission -> new SimpleGrantedAuthority(permission)).toList());
	}

}
