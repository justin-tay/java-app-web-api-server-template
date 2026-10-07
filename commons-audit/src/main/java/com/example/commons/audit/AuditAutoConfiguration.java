package com.example.commons.audit;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configures the {@link AuditTrail} (see docs/adr/0040) whenever commons-audit is on the
 * classpath.
 *
 * <p>
 * The entity and repository in {@code com.example.commons.audit} are registered through
 * {@link AutoConfigurationPackage}, which adds this package to the application's own
 * entity and repository scanning rather than replacing it, as {@code @EntityScan} would.
 * The schema is the Liquibase changelog
 * {@code com/example/commons/audit/jdbc/schema.yaml} on this module's classpath, with a
 * SQL script per database beside it, which the application's master changelog includes or
 * its own migration tool applies.
 */
@AutoConfiguration(before = { HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class })
@AutoConfigurationPackage
public class AuditAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AuditTrail auditTrail(AuditTrailEventRepository events, PlatformTransactionManager transactionManager) {
		return new AuditTrail(events, transactionManager, Clock.systemUTC());
	}

}
