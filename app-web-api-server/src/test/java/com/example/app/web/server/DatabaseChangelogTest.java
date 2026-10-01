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

		assertThat(names("app_role")).containsExactlyInAnyOrder("USER_MANAGE", "GROUP_MANAGE", "ROLE_MANAGE",
				"APPLICATION_USER", "ACCOUNT_REVIEWER", "SETTINGS_MANAGE");
		assertThat(names("app_group")).containsExactlyInAnyOrder("Administrators", "Account Reviewers");
		assertThat(count("app_group_role")).isEqualTo(5);
		assertThat(this.jdbcTemplate
			.queryForObject("SELECT COUNT(*) FROM app_group_role gr JOIN app_group g ON g.id = gr.group_id "
					+ "JOIN app_role r ON r.id = gr.role_id WHERE g.name = 'Account Reviewers' AND r.name = 'ACCOUNT_REVIEWER'",
					Integer.class))
			.isEqualTo(1);
		assertThat(this.jdbcTemplate
			.queryForObject("SELECT COUNT(*) FROM app_group_role gr JOIN app_group g ON g.id = gr.group_id "
					+ "JOIN app_role r ON r.id = gr.role_id WHERE g.name = 'Administrators' AND r.name = 'ACCOUNT_REVIEWER'",
					Integer.class))
			.as("administrators do not review accounts")
			.isZero();
		assertThat(settings()).containsEntry("inactivity.enabled", "true")
			.containsEntry("inactivity.suspendAfterDays", "90")
			.containsEntry("inactivity.removeAfterDays", "180")
			.containsEntry("review.enabled", "true")
			.containsEntry("review.intervalMonths", "3");
		assertThat(count("app_user")).isZero();
		assertThat(count("app_user_group")).isZero();
	}

	@Test
	void migrationWithAnotherContextCreatesNoUsers() throws Exception {
		migrate(this.database, "production");

		assertThat(count("app_user")).isZero();
		assertThat(names("app_group")).containsExactlyInAnyOrder("Administrators", "Account Reviewers");
	}

	@Test
	void migrationWithTheDevContextAlsoCreatesTheDevelopmentUsers() throws Exception {
		migrate(this.database, "dev");

		assertThat(this.jdbcTemplate.queryForList("SELECT username FROM app_user", String.class))
			.containsExactlyInAnyOrder("admin", "user", "multi-group-user", "account-reviewer-1", "account-reviewer-2");
		assertThat(names("app_group")).containsExactlyInAnyOrder("Administrators", "Users", "Account Reviewers");
		assertThat(this.jdbcTemplate.queryForList("SELECT status FROM app_user", String.class)).containsOnly("ACTIVE");
		assertThat(this.jdbcTemplate.queryForList("SELECT DISTINCT u.username FROM app_user u "
				+ "JOIN app_user_group ug ON ug.user_id = u.id JOIN app_group_role gr ON gr.group_id = ug.group_id "
				+ "JOIN app_role r ON r.id = gr.role_id WHERE r.name = 'ACCOUNT_REVIEWER'", String.class))
			.containsExactlyInAnyOrder("account-reviewer-1", "account-reviewer-2");
		assertThat(settings()).as("automation is off for development data")
			.containsEntry("inactivity.enabled", "false")
			.containsEntry("review.enabled", "false")
			.containsEntry("inactivity.suspendAfterDays", "90");
		assertThat(this.jdbcTemplate.queryForList("""
				SELECT DISTINCT r.name FROM app_user u
				JOIN app_user_group ug ON ug.user_id = u.id
				JOIN app_group_role gr ON gr.group_id = ug.group_id
				JOIN app_role r ON r.id = gr.role_id
				WHERE u.username = 'admin'
				""", String.class)).containsExactlyInAnyOrder("USER_MANAGE", "GROUP_MANAGE", "ROLE_MANAGE",
				"SETTINGS_MANAGE");
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

	private int count(String table) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
