package com.example.app.web.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
				"APPLICATION_USER");
		assertThat(names("app_group")).containsExactly("Administrators");
		assertThat(count("app_group_role")).isEqualTo(3);
		assertThat(count("app_user")).isZero();
		assertThat(count("app_user_group")).isZero();
	}

	@Test
	void migrationWithAnotherContextCreatesNoUsers() throws Exception {
		migrate(this.database, "production");

		assertThat(count("app_user")).isZero();
		assertThat(names("app_group")).containsExactly("Administrators");
	}

	@Test
	void migrationWithTheDevContextAlsoCreatesTheDevelopmentUsers() throws Exception {
		migrate(this.database, "dev");

		assertThat(this.jdbcTemplate.queryForList("SELECT username FROM app_user", String.class))
			.containsExactlyInAnyOrder("admin", "test-user", "multi-group-user");
		assertThat(names("app_group")).containsExactlyInAnyOrder("Administrators", "Test Users");
		assertThat(this.jdbcTemplate.queryForList("""
				SELECT DISTINCT r.name FROM app_user u
				JOIN app_user_group ug ON ug.user_id = u.id
				JOIN app_group_role gr ON gr.group_id = ug.group_id
				JOIN app_role r ON r.id = gr.role_id
				WHERE u.username = 'admin'
				""", String.class)).containsExactlyInAnyOrder("USER_MANAGE", "GROUP_MANAGE", "ROLE_MANAGE");
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

	private int count(String table) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
