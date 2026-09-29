package com.example.commons.accounts;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.commons.accounts.admin.AdminReauthenticationInterceptor;
import com.example.commons.accounts.admin.AdministrationAuditLogger;
import com.example.commons.accounts.admin.AdministrationService;
import com.example.commons.accounts.admin.GroupAdminController;
import com.example.commons.accounts.admin.RoleAdminController;
import com.example.commons.accounts.admin.UserAdminController;
import com.example.commons.accounts.admin.UserPasskeyAdminController;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.WebSecurityAutoConfiguration;
import com.example.commons.security.authentication.passkey.PasskeyManager;
import com.example.commons.security.authentication.passkey.PasskeyUserDirectory;
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
		after = WebSecurityAutoConfiguration.class,
		afterName = "com.example.commons.security.authentication.passkey.PasskeySecurityAutoConfiguration")
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
	 * Records each user's last sign-in time (see docs/adr/0028).
	 * @param users the user repository
	 * @return the recorder
	 */
	@Bean
	@ConditionalOnMissingBean(LastLoginRecorder.class)
	LastLoginRecorder lastLoginRecorder(AppUserRepository users) {
		return new LastLoginRecorder(users, Clock.systemUTC());
	}

	/**
	 * Finds the local user a passkey is registered to, when passkeys are enabled.
	 * @param users the user repository
	 * @return the passkey user directory
	 */
	@Bean
	@ConditionalOnBooleanProperty(name = "commons.security.passkeys.enabled")
	@ConditionalOnMissingBean(PasskeyUserDirectory.class)
	AppUserPasskeyUserDirectory appUserPasskeyUserDirectory(AppUserRepository users) {
		return new AppUserPasskeyUserDirectory(users);
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
				AdministrationAuditLogger administrationAuditLogger, ObjectProvider<PasskeyManager> passkeyManager) {
			return new AdministrationService(users, groups, roles, sessionRevocationService, administrationAuditLogger,
					passkeyManager.getIfAvailable());
		}

		/**
		 * The administrator's view of a user's passkeys, when passkeys are enabled.
		 * @param administrationService the administration service
		 * @param passkeyManager the passkey manager
		 * @return the controller
		 */
		@Bean
		@ConditionalOnBean(PasskeyManager.class)
		UserPasskeyAdminController userPasskeyAdminController(AdministrationService administrationService,
				PasskeyManager passkeyManager) {
			return new UserPasskeyAdminController(administrationService, passkeyManager);
		}

		/**
		 * Requires a login no older than
		 * {@code commons.accounts.admin.reauthentication-max-age} (15 minutes by default)
		 * for every change made through the administration API, except ending sessions.
		 * @param environment the environment
		 * @return the MVC configurer that registers the check
		 */
		@Bean
		WebMvcConfigurer adminReauthenticationConfigurer(Environment environment) {
			Duration maxAge = environment.getProperty("commons.accounts.admin.reauthentication-max-age", Duration.class,
					Duration.ofMinutes(15));
			AdminReauthenticationInterceptor interceptor = new AdminReauthenticationInterceptor(maxAge,
					Clock.systemUTC());
			return new WebMvcConfigurer() {

				@Override
				public void addInterceptors(InterceptorRegistry registry) {
					registry.addInterceptor(interceptor)
						.addPathPatterns("/admin/users/**", "/admin/groups/**", "/admin/roles/**")
						.excludePathPatterns("/admin/users/sessions", "/admin/users/*/sessions");
				}

			};
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
