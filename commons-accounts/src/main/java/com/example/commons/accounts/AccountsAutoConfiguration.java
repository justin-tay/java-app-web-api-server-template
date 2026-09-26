package com.example.commons.accounts;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.commons.accounts.admin.AdministrationAuditLogger;
import com.example.commons.accounts.admin.AdministrationService;
import com.example.commons.accounts.admin.GroupAdminController;
import com.example.commons.accounts.admin.RoleAdminController;
import com.example.commons.accounts.admin.UserAdminController;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.WebSecurityAutoConfiguration;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Configures local account management: the user, group, and role model the application's
 * authorities come from (see docs/adr/0005), a {@link LocalAuthorityLookup} backed by it,
 * and the administration API under {@code /admin/users}, {@code /admin/groups}, and
 * {@code /admin/roles}.
 *
 * <p>
 * The entities and repositories in {@code com.example.commons.accounts.domain} are
 * registered through {@link AutoConfigurationPackage}, which adds this package to the
 * application's own entity and repository scanning rather than replacing it, as
 * {@code @EntityScan} would. The schema is the Liquibase changelog
 * {@code db/changelog/001-authorisation-schema.sql} on this module's classpath, which the
 * application's master changelog includes; a backend that only reads a user store another
 * backend owns leaves it out.
 *
 * <p>
 * This module ships no data. The application seeds its own roles and groups, and the
 * administration API requires three of them by name: {@code USER_MANAGE},
 * {@code GROUP_MANAGE}, and {@code ROLE_MANAGE}, which its controllers check with
 * {@code @PreAuthorize}. Without them no user can hold those roles, so the API denies
 * every request.
 *
 * <p>
 * Applied whenever commons-accounts is on the classpath of a servlet application. Set
 * {@code commons.accounts.enabled=false} to turn it off, or
 * {@code commons.accounts.admin.enabled=false} to keep the model and the authority lookup
 * without the administration API. Each admin controller also enforces its management role
 * with {@code @PreAuthorize}, so the API stays protected whatever URL rules an
 * application's filter chain has.
 */
@AutoConfiguration(before = { HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class },
		after = WebSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = "commons.accounts.enabled", matchIfMissing = true)
@AutoConfigurationPackage
public class AccountsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(LocalAuthorityLookup.class)
	AppUserLocalAuthorityLookup appUserLocalAuthorityLookup(AppUserRepository users) {
		return new AppUserLocalAuthorityLookup(users);
	}

	/**
	 * The administration API.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBooleanProperty(name = "commons.accounts.admin.enabled", matchIfMissing = true)
	static class AdministrationConfiguration {

		@Bean
		AdministrationAuditLogger administrationAuditLogger() {
			return new AdministrationAuditLogger();
		}

		@Bean
		AdministrationService administrationService(AppUserRepository users, AppGroupRepository groups,
				AppRoleRepository roles, SessionRevocationService sessionRevocationService,
				AdministrationAuditLogger administrationAuditLogger) {
			return new AdministrationService(users, groups, roles, sessionRevocationService, administrationAuditLogger);
		}

		@Bean
		UserAdminController userAdminController(AdministrationService administrationService) {
			return new UserAdminController(administrationService);
		}

		@Bean
		GroupAdminController groupAdminController(AdministrationService administrationService) {
			return new GroupAdminController(administrationService);
		}

		@Bean
		RoleAdminController roleAdminController(AdministrationService administrationService) {
			return new RoleAdminController(administrationService);
		}

	}

}
