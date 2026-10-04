package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.commons.accounts.domain.AccountAuditEvent;
import com.example.commons.accounts.domain.AppGroup;
import com.example.commons.accounts.domain.AppRole;
import com.example.commons.accounts.domain.AppSetting;
import com.example.commons.accounts.domain.AppUser;
import com.example.commons.accounts.domain.ReviewItem;
import com.example.commons.accounts.domain.Task;

/**
 * Runs the shipped SQL scripts on real PostgreSQL and SQL Server servers, which H2 cannot
 * stand in for: H2 accepts types that mean something else on SQL Server (a
 * {@code TIMESTAMP} column there is a row version, not a date). The scripts are applied,
 * then a user and a passkey credential are written and read back, which exercises the
 * UUID, timestamp, boolean and binary types and the foreign key. Skipped when Docker is
 * not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class SchemaScriptsContainerTest {

	private static final String PASSWORD = "Test-password-1";

	@Test
	void postgresqlScriptsCreateAWorkingSchema() throws Exception {
		try (GenericContainer<?> container = new GenericContainer<>("postgres:17-alpine")
			.withEnv("POSTGRES_PASSWORD", PASSWORD)
			.withExposedPorts(5432)
			.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))) {
			container.start();
			verify("postgresql",
					"jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/postgres",
					"postgres");
		}
	}

	@Test
	void sqlServerScriptsCreateAWorkingSchema() throws Exception {
		try (GenericContainer<?> container = new GenericContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
			.withEnv("ACCEPT_EULA", "Y")
			.withEnv("MSSQL_SA_PASSWORD", PASSWORD)
			.withExposedPorts(1433)
			.waitingFor(Wait.forLogMessage(".*SQL Server is now ready for client connections.*\\n", 1))) {
			container.start();
			verify("sqlserver", "jdbc:sqlserver://" + container.getHost() + ":" + container.getMappedPort(1433)
					+ ";encrypt=false;trustServerCertificate=true", "sa");
		}
	}

	/**
	 * Connects, retrying for a while because a server logs that it is ready slightly
	 * before it accepts logins.
	 */
	private static Connection connect(String url, String user) throws SQLException, InterruptedException {
		SQLException failure = null;
		for (int attempt = 0; attempt < 30; attempt++) {
			try {
				return DriverManager.getConnection(url, user, PASSWORD);
			}
			catch (SQLException ex) {
				failure = ex;
				Thread.sleep(1000);
			}
		}
		throw failure;
	}

	/**
	 * Has Hibernate validate the entity mappings against the schema the scripts created,
	 * as {@code spring.jpa.hibernate.ddl-auto=validate} does in AccountsJpaTest on H2, so
	 * a column type the database and Hibernate disagree about fails here.
	 */
	private static void validateEntityMappings(String url, String user) {
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
			.applySetting("hibernate.connection.url", url)
			.applySetting("hibernate.connection.username", user)
			.applySetting("hibernate.connection.password", PASSWORD)
			.applySetting("hibernate.hbm2ddl.auto", "validate")
			.applySetting("hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName())
			.build();
		try {
			MetadataSources sources = new MetadataSources(registry);
			for (Class<?> entity : List.of(AppUser.class, AppGroup.class, AppRole.class, AppSetting.class,
					AccountAuditEvent.class, Task.class, ReviewItem.class)) {
				sources.addAnnotatedClass(entity);
			}
			sources.buildMetadata().buildSessionFactory().close();
		}
		finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

	private static void verify(String platform, String url, String user) throws Exception {
		try (Connection connection = connect(url, user)) {
			ScriptUtils.executeSqlScript(connection,
					new ClassPathResource("com/example/commons/session/jdbc/schema-" + platform + ".sql"));
			ScriptUtils.executeSqlScript(connection,
					new ClassPathResource("com/example/commons/session/oidc/jdbc/schema-" + platform + ".sql"));
			ScriptUtils.executeSqlScript(connection,
					new ClassPathResource("com/example/commons/accounts/jdbc/schema-" + platform + ".sql"));
			validateEntityMappings(url, user);

			UUID id = UUID.randomUUID();
			Timestamp now = Timestamp.from(Instant.now());
			try (PreparedStatement insert = connection.prepareStatement("INSERT INTO app_user (id, username, name, "
					+ "created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
				insert.setObject(1, id);
				insert.setString(2, "alice");
				insert.setString(3, "Alice Tan");
				insert.setTimestamp(4, now);
				insert.setTimestamp(5, now);
				insert.setString(6, "system");
				insert.setString(7, "system");
				insert.executeUpdate();
			}
			try (PreparedStatement select = connection
				.prepareStatement("SELECT status, inactivity_clock_started_at FROM app_user WHERE id = ?")) {
				select.setObject(1, id);
				try (ResultSet rows = select.executeQuery()) {
					assertThat(rows.next()).isTrue();
					assertThat(rows.getString(1)).isEqualTo("ACTIVE");
					assertThat(rows.getTimestamp(2)).isNotNull();
				}
			}

			byte[] key = { 1, 2, 3 };
			try (PreparedStatement entity = connection
				.prepareStatement("INSERT INTO user_entities (id, name) VALUES ('handle', 'alice')");
					PreparedStatement credential = connection.prepareStatement("INSERT INTO user_credentials "
							+ "(credential_id, user_entity_user_id, public_key, backup_eligible, backup_state, label) "
							+ "VALUES ('cred', 'handle', ?, ?, ?, 'laptop')")) {
				entity.executeUpdate();
				credential.setBytes(1, key);
				credential.setBoolean(2, false);
				credential.setBoolean(3, false);
				credential.executeUpdate();
			}
			try (ResultSet rows = connection.createStatement()
				.executeQuery("SELECT public_key FROM user_credentials WHERE credential_id = 'cred'")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getBytes(1)).isEqualTo(key);
			}
			connection.createStatement().executeUpdate("DELETE FROM user_entities WHERE id = 'handle'");
			try (ResultSet rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM user_credentials")) {
				rows.next();
				assertThat(rows.getInt(1)).as("credentials of a deleted user entity are deleted with it").isZero();
			}

			// The drop scripts remove everything in an order the foreign keys allow, and
			// the schema
			// can then be created again.
			for (String folder : new String[] { "accounts/jdbc", "session/oidc/jdbc", "session/jdbc" }) {
				ScriptUtils.executeSqlScript(connection,
						new ClassPathResource("com/example/commons/" + folder + "/schema-drop-" + platform + ".sql"));
			}
			try (ResultSet rows = connection.createStatement()
				.executeQuery(
						"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) IN ('app_user', 'spring_session', 'oidc_session')")) {
				rows.next();
				assertThat(rows.getInt(1)).as("tables left after the drop scripts").isZero();
			}
			ScriptUtils.executeSqlScript(connection,
					new ClassPathResource("com/example/commons/accounts/jdbc/schema-" + platform + ".sql"));
		}
	}

}
