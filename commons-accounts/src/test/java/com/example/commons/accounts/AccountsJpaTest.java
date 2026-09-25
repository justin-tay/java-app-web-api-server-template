package com.example.commons.accounts;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * A {@link DataJpaTest} of the accounts model against an in-memory H2 database whose
 * schema is this module's Liquibase changelog, {@code 001-authorisation-schema.sql}.
 * Hibernate validates the entity mappings against that schema rather than generating its
 * own, so a mapping that drifts from the changelog fails the test. The configuration is
 * {@link AccountsTestConfiguration}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(properties = { "spring.liquibase.change-log=classpath:db/changelog/accounts-test-changelog.yaml",
		"spring.jpa.hibernate.ddl-auto=validate" })
public @interface AccountsJpaTest {

}
