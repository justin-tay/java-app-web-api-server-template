package com.example.commons.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import liquibase.command.CommandScope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

/**
 * Checks that the {@code schema-<platform>.sql} and {@code schema-drop-<platform>.sql}
 * scripts shipped beside {@code schema.yaml} are what Liquibase generates from it, so
 * consumers who do not use Liquibase get the same schema as those who do. Run with
 * {@code -Dschema.regenerate=true} to rewrite the scripts after changing
 * {@code schema.yaml}.
 */
class SchemaSqlTest {

	/** The folders of the schemas the session module ships. */
	private static final List<String> DIRECTORIES = List.of("com/example/commons/session/jdbc",
			"com/example/commons/session/oidc/jdbc");

	/** The script platform and Liquibase's name for it. */
	private static final List<String[]> PLATFORMS = List.of(new String[] { "h2", "h2" },
			new String[] { "postgresql", "postgresql" }, new String[] { "sqlserver", "mssql" });

	@Test
	void shippedScriptsMatchTheChangelog() throws Exception {
		boolean regenerate = Boolean.getBoolean("schema.regenerate");
		for (String directory : DIRECTORIES) {
			for (String[] platform : PLATFORMS) {
				Path state = Files.createTempFile("schema-state", ".csv");
				try {
					check(directory, "schema-" + platform[0] + ".sql",
							generate(directory, platform[1], state, "updateSql", "-- Changeset"), regenerate);
					check(directory, "schema-drop-" + platform[0] + ".sql",
							generate(directory, platform[1], state, "rollbackCountSql", "-- Rolling Back ChangeSet"),
							regenerate);
				}
				finally {
					Files.deleteIfExists(state);
				}
			}
		}
	}

	private static void check(String directory, String name, String generated, boolean regenerate) throws Exception {
		Path script = Path.of("src/main/resources", directory, name);
		if (regenerate) {
			Files.writeString(script, generated, StandardCharsets.UTF_8);
		}
		assertThat(Files.readString(script, StandardCharsets.UTF_8).replace("\r\n", "\n"))
			.as("%s is out of date; run the tests with -Dschema.regenerate=true", script)
			.isEqualTo(generated);
	}

	/**
	 * Renders the SQL of a Liquibase command against an offline database whose state,
	 * which changesets have run, is kept in {@code state} so that the rollback follows
	 * the update.
	 */
	private static String generate(String directory, String liquibasePlatform, Path state, String command, String start)
			throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Database database = DatabaseFactory.getInstance()
			.openDatabase(
					"offline:" + liquibasePlatform + "?outputLiquibaseSql=none&changeLogFile="
							+ state.toString().replace(File.separatorChar, '/'),
					null, null, null, new ClassLoaderResourceAccessor());
		database.setOutputDefaultSchema(false);
		database.setOutputDefaultCatalog(false);
		CommandScope scope = new CommandScope(command).addArgumentValue("database", database)
			.addArgumentValue("changelogFile", directory + "/schema.yaml")
			.setOutput(out);
		if (command.equals("rollbackCountSql")) {
			scope.addArgumentValue("count", 1);
		}
		scope.execute();
		return normalise(out.toString(StandardCharsets.UTF_8), start);
	}

	/**
	 * Drops Liquibase's run-specific header, the changeset comment of a drop script, and
	 * SQL Server's {@code GO} batch separators, which JDBC clients do not understand,
	 * leaving statements that each end in a semicolon.
	 */
	private static String normalise(String output, String start) {
		String text = output.replace("\r\n", "\n");
		text = text.substring(text.indexOf(start));
		if (start.startsWith("-- Rolling")) {
			text = text.substring(text.indexOf('\n') + 1);
		}
		text = text.replaceAll("(?<!;)\nGO\n", ";\n").replaceAll("(?m)^GO\n", "").replaceAll("\n{3,}", "\n\n");
		return text.strip() + "\n";
	}

}
