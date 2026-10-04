package com.example.commons.accounts;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints for a GraalVM native image of an application using commons-accounts,
 * registered through {@code META-INF/spring/aot.factories} so no application has to
 * import them. The accounts schema is a classpath resource, a Liquibase changelog and one
 * SQL script per database, that the application's migration reads at startup, which AOT's
 * static analysis cannot see.
 */
class AccountsRuntimeHints implements RuntimeHintsRegistrar {

	/** The Liquibase changelog and SQL scripts of the accounts schema. */
	static final String SCHEMA_PATTERN = "com/example/commons/accounts/jdbc/schema*";

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerPattern(SCHEMA_PATTERN);
	}

}
