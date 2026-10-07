package com.example.app.web.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Tests which data the Liquibase changelog creates with and without the {@code dev}
 * context, so a production migration, which requests no {@code dev} context, never
 * creates the development users. See docs/adr/0018.
 */
class DatabaseChangelogTest {

	private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
		.generateUniqueName(true)
		.build();

	private final JdbcTemplate jdbcTemplate = new JdbcTemplate(this.database);

	@AfterEach
	void shutDown() {
		this.database.shutdown();
	}

	@Test
	void migrationWithoutAContextCreatesReferenceDataButNoUsers() throws Exception {
		migrate(this.database, null);

		assertThat(names("app_role")).containsExactlyInAnyOrder("Administrators", "Account Reviewers");
		assertThat(this.jdbcTemplate.queryForList("SELECT domain || ':' || action FROM app_permission", String.class))
			.hasSize(25)
			.contains("user:create", "user:add-role", "role:add-permission", "settings:update", "review:decide");
		assertThat(this.jdbcTemplate
			.queryForList("SELECT domain || ':' || action FROM app_permission WHERE privileged = TRUE", String.class))
			.containsExactlyInAnyOrder("user:create", "user:add-role", "user:unsuspend", "role:add-permission",
					"settings:update");
		assertThat(rolePermissions("Account Reviewers")).containsExactlyInAnyOrder("application:access", "user:read",
				"user:remove-role", "user:remove", "role:read", "audit:read", "review:read", "review:decide",
				"review:confirm-population", "review:download-report");
		assertThat(rolePermissions("Administrators")).hasSize(21)
			.contains("user:create", "user:add-role", "role:add-permission", "settings:update", "audit:read")
			.doesNotContain("review:decide")
			.as("administrators do not review accounts");
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM app_role_permission rp JOIN app_permission p ON p.id = rp.permission_id
				WHERE rp.role_id = 19 AND p.privileged = TRUE
				""", Integer.class)).as("reviewers hold no privileged permission").isZero();
		assertThat(this.jdbcTemplate.queryForList("""
				SELECT a.domain || ':' || a.action FROM app_permission_conflict c
				JOIN app_permission a ON a.id = c.permission_id
				""", String.class)).containsOnly("review:decide").hasSize(5);
		assertThat(settings()).containsEntry("inactivity.enabled", "true")
			.containsEntry("inactivity.suspendAfterDays", "90")
			.containsEntry("inactivity.removeAfterDays", "180")
			.containsEntry("review.enabled", "true")
			.containsEntry("review.privilegedIntervalMonths", "1")
			.containsEntry("review.nonPrivilegedIntervalMonths", "12");
		assertThat(count("app_user")).isZero();
		assertThat(count("app_user_role")).isZero();
	}

	@Test
	void migrationWithAnotherContextCreatesNoUsers() throws Exception {
		migrate(this.database, "production");

		assertThat(count("app_user")).isZero();
		assertThat(names("app_role")).containsExactlyInAnyOrder("Administrators", "Account Reviewers");
	}

	@Test
	void migrationWithTheDevContextAlsoCreatesTheDevelopmentUsers() throws Exception {
		migrate(this.database, "dev");

		assertThat(this.jdbcTemplate.queryForList("SELECT username FROM app_user", String.class))
			.containsExactlyInAnyOrder("admin", "user", "multi-group-user", "account-reviewer-1", "account-reviewer-2");
		assertThat(names("app_role")).containsExactlyInAnyOrder("Administrators", "Users", "Account Reviewers");
		assertThat(departments()).containsEntry("admin", "IT")
			.containsEntry("user", "Finance")
			.containsEntry("multi-group-user", "Operations")
			.containsEntry("account-reviewer-1", "Compliance")
			.containsEntry("account-reviewer-2", "Compliance");
		assertThat(this.jdbcTemplate.queryForList("SELECT status FROM app_user", String.class)).containsOnly("ACTIVE");
		assertThat(this.jdbcTemplate.queryForList("""
				SELECT DISTINCT u.username FROM app_user u
				JOIN app_user_role ur ON ur.user_id = u.id
				JOIN app_role_permission rp ON rp.role_id = ur.role_id
				JOIN app_permission p ON p.id = rp.permission_id
				WHERE p.domain = 'review' AND p.action = 'decide'
				""", String.class)).containsExactlyInAnyOrder("account-reviewer-1", "account-reviewer-2");
		assertThat(this.jdbcTemplate.queryForList("""
				SELECT DISTINCT u.username FROM app_user u
				JOIN app_user_role ur ON ur.user_id = u.id
				JOIN app_role_permission rp ON rp.role_id = ur.role_id
				JOIN app_permission p ON p.id = rp.permission_id
				WHERE p.privileged = TRUE
				""", String.class)).containsExactlyInAnyOrder("admin", "multi-group-user");
		assertThat(settings()).as("automation is off for development data")
			.containsEntry("inactivity.enabled", "false")
			.containsEntry("review.enabled", "false")
			.containsEntry("inactivity.suspendAfterDays", "90");
	}

	@Test
	void migrationWithTheDemoContextAlsoCreatesSampleAccountsToReview() throws Exception {
		migrate(this.database, "dev,demo");

		assertThat(count("app_user")).isEqualTo(17);
		assertThat(this.jdbcTemplate.queryForList("SELECT DISTINCT status FROM app_user", String.class))
			.containsExactlyInAnyOrder("ACTIVE", "SUSPENDED");
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE last_login_at IS NULL "
				+ "AND status = 'ACTIVE' AND username IN ('kumar.raj', 'mei.ling.tan')", Integer.class))
			.isEqualTo(2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = 'delete_user'",
				Integer.class))
			.isEqualTo(2);
		assertThat(settings()).containsEntry("review.enabled", "true")
			.containsEntry("review.privilegedIntervalMonths", "1")
			.containsEntry("review.nonPrivilegedIntervalMonths", "1")
			.containsEntry("inactivity.enabled", "false");
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM app_user WHERE department IS NULL AND status = 'ACTIVE'", Integer.class))
			.isZero();
	}

	@Test
	void migrationWithoutTheDemoContextCreatesNoSampleAccounts() throws Exception {
		migrate(this.database, "dev");

		assertThat(count("app_user")).isEqualTo(5);
		assertThat(count("audit_event")).isZero();
		assertThat(count("task")).isZero();
		assertThat(count("account_review_item")).isZero();
		assertThat(count("account_review_attestation")).isZero();
		assertThat(count("account_review_population_entry")).isZero();
		assertThat(count("account_review_report")).isZero();
	}

	@Test
	void deletingAPasskeyUserEntityDeletesItsCredentials() throws Exception {
		migrate(this.database, null);
		this.jdbcTemplate
			.update("INSERT INTO user_entities (id, name, display_name) VALUES ('handle', 'alice', 'Alice')");
		this.jdbcTemplate.update("""
				INSERT INTO user_credentials (credential_id, user_entity_user_id, public_key, backup_eligible,
				backup_state, label) VALUES ('cred', 'handle', X'01', FALSE, FALSE, 'laptop')
				""");

		this.jdbcTemplate.update("DELETE FROM user_entities WHERE id = 'handle'");

		assertThat(count("user_credentials")).isZero();
	}

	private static void migrate(DataSource dataSource, String contexts) throws Exception {
		SpringLiquibase liquibase = new SpringLiquibase();
		liquibase.setDataSource(dataSource);
		liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
		liquibase.setContexts(contexts);
		liquibase.setResourceLoader(new DefaultResourceLoader());
		liquibase.afterPropertiesSet();
	}

	private List<String> rolePermissions(String role) {
		return this.jdbcTemplate.queryForList("""
				SELECT p.domain || ':' || p.action FROM app_role r
				JOIN app_role_permission rp ON rp.role_id = r.id
				JOIN app_permission p ON p.id = rp.permission_id
				WHERE r.name = ?
				""", String.class, role);
	}

	private List<String> names(String table) {
		return this.jdbcTemplate.queryForList("SELECT name FROM " + table, String.class);
	}

	private Map<String, String> settings() {
		return this.jdbcTemplate.query("SELECT name, setting_value FROM app_setting", rs -> {
			Map<String, String> values = new HashMap<>();
			while (rs.next()) {
				values.put(rs.getString(1), rs.getString(2));
			}
			return values;
		});
	}

	private Map<String, String> departments() {
		return this.jdbcTemplate.query("SELECT username, department FROM app_user", rs -> {
			Map<String, String> values = new HashMap<>();
			while (rs.next()) {
				values.put(rs.getString(1), rs.getString(2));
			}
			return values;
		});
	}

	private int count(String table) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
