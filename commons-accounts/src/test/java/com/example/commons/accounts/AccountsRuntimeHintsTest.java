package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;

class AccountsRuntimeHintsTest {

	@Test
	void includesTheAccountsSchemaInANativeImage() {
		RuntimeHints hints = new RuntimeHints();
		SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
			.load(RuntimeHintsRegistrar.class)
			.forEach(registrar -> registrar.registerHints(hints, getClass().getClassLoader()));

		for (String script : new String[] { "schema.yaml", "schema-h2.sql", "schema-postgresql.sql",
				"schema-sqlserver.sql" }) {
			assertThat(RuntimeHintsPredicates.resource().forResource("com/example/commons/accounts/jdbc/" + script))
				.accepts(hints);
		}
	}

}
