package com.example.commons.accounts;

import java.time.Clock;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.commons.audit.AuditTrail;
import com.example.commons.audit.AuditTrailEventRepository;

/**
 * The configuration {@link AccountsJpaTest} finds by searching up from a test's package.
 * Its package, {@code com.example.commons.accounts}, is the auto-configuration package,
 * so the entities and repositories in {@code domain} are scanned. The audit trail's
 * package is added, as commons-audit's auto-configuration adds it in an application, and
 * the {@link AuditTrail} built on it.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@AutoConfigurationPackage(basePackageClasses = { AccountsTestConfiguration.class, AuditTrail.class })
class AccountsTestConfiguration {

	@Bean
	AuditTrail auditTrail(AuditTrailEventRepository events, PlatformTransactionManager transactionManager) {
		return new AuditTrail(events, transactionManager, Clock.systemUTC());
	}

}
