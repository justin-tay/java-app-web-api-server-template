package com.example.commons.accounts;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.commons.accounts.admin.AdminReauthenticationInterceptor;
import com.example.commons.accounts.admin.AccountAuditLogger;
import com.example.commons.accounts.admin.AccountLifecycleService;
import com.example.commons.accounts.admin.AuditEventController;
import com.example.commons.accounts.admin.AdministrationService;
import com.example.commons.accounts.admin.GroupAdminController;
import com.example.commons.accounts.admin.RoleAdminController;
import com.example.commons.accounts.admin.UserAdminController;
import com.example.commons.accounts.admin.UserPasskeyAdminController;
import com.example.commons.accounts.domain.AccountAuditEventRepository;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AccountReviewAttestationRepository;
import com.example.commons.accounts.domain.AccountReviewItemRepository;
import com.example.commons.accounts.domain.AccountReviewPopulationEntryRepository;
import com.example.commons.accounts.domain.AccountReviewReportRepository;
import com.example.commons.accounts.domain.AppSettingRepository;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.accounts.domain.TaskRepository;
import com.example.commons.accounts.review.AccountReviewController;
import com.example.commons.accounts.review.AccountReviewScheduler;
import com.example.commons.accounts.review.AccountReviewService;
import com.example.commons.accounts.review.AccountReviewReports;
import com.example.commons.accounts.review.DefaultReviewReportRenderer;
import com.example.commons.accounts.review.ReviewItems;
import com.example.commons.accounts.review.ReviewReportRenderer;
import com.example.commons.accounts.review.TaskController;
import com.example.commons.accounts.settings.SettingsController;
import com.example.commons.accounts.settings.SettingsService;
import com.example.commons.security.WebSecurityAutoConfiguration;
import com.example.commons.security.authentication.passkey.PasskeyManager;
import com.example.commons.security.authentication.passkey.PasskeyUserDirectory;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.session.SessionRevocationService;

/**
 * Configures local account management: the user, group, and role model the application's
 * authorities come from (see docs/adr/0005), a {@link LocalAuthorityLookup} backed by it,
 * and the administration API under {@code /admin/users}, {@code /admin/groups},
 * {@code /admin/roles}, and {@code /admin/settings}.
 *
 * <p>
 * The entities and repositories in {@code com.example.commons.accounts.domain} are
 * registered through {@link AutoConfigurationPackage}, which adds this package to the
 * application's own entity and repository scanning rather than replacing it, as
 * {@code @EntityScan} would. The schema is the Liquibase changelog
 * {@code com/example/commons/accounts/jdbc/schema.yaml} on this module's classpath, with
 * a SQL script per database beside it, which the application's master changelog includes
 * or its own migration tool applies; a backend that only reads a user store another
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
	 * Audit logging of changes to accounts, groups, roles, settings, and reviews, as log
	 * events and as rows of the business audit trail (see docs/adr/0030).
	 * @param events the audit event repository
	 * @return the audit logger
	 */
	@Bean
	@ConditionalOnMissingBean
	AccountAuditLogger accountAuditLogger(AccountAuditEventRepository events) {
		return new AccountAuditLogger(events, Clock.systemUTC());
	}

	/**
	 * Suspends, unsuspends, and removes accounts, for the administration API, the account
	 * review, and the inactivity job (see docs/adr/0031).
	 * @param users the user repository
	 * @param sessionRevocationService the session revocation service
	 * @param auditLogger the audit logger
	 * @param passkeyManager the passkey manager, when passkeys are enabled
	 * @return the service
	 */
	@Bean
	@ConditionalOnMissingBean
	AccountLifecycleService accountLifecycleService(AppUserRepository users,
			SessionRevocationService sessionRevocationService, AccountAuditLogger auditLogger,
			ObjectProvider<PasskeyManager> passkeyManager, ReviewItems reviewItems) {
		return new AccountLifecycleService(users, sessionRevocationService, auditLogger,
				passkeyManager.getIfAvailable(), reviewItems, Clock.systemUTC());
	}

	/**
	 * Keeps review items consistent when an account is removed (see docs/adr/0037).
	 * @param items the review item repository
	 * @return the component
	 */
	@Bean
	@ConditionalOnMissingBean
	ReviewItems reviewItems(AccountReviewItemRepository items) {
		return new ReviewItems(items, Clock.systemUTC());
	}

	/**
	 * The application settings (see docs/adr/0031).
	 * @param settings the setting repository
	 * @param auditLogger the audit logger
	 * @return the service
	 */
	@Bean
	@ConditionalOnMissingBean
	SettingsService settingsService(AppSettingRepository settings, AccountAuditLogger auditLogger) {
		return new SettingsService(settings, auditLogger);
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
	 * Suspends and removes inactive accounts according to the {@code inactivity.*}
	 * settings, checking every {@code commons.accounts.inactivity.check-interval} (one
	 * hour by default; see docs/adr/0031).
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class InactivityConfiguration {

		@Bean
		@ConditionalOnMissingBean
		InactiveUserSuspender inactiveUserSuspender(AppUserRepository users, AccountLifecycleService lifecycle,
				SettingsService settings) {
			return new InactiveUserSuspender(users, lifecycle, settings, Clock.systemUTC());
		}

	}

	/**
	 * Creates the periodic account review task according to the {@code review.*}
	 * settings, checking every {@code commons.accounts.review.check-interval} (one hour
	 * by default), with review months taken from the calendar in
	 * {@code commons.accounts.review.time-zone} (the system time zone by default; see
	 * docs/adr/0037).
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class AccountReviewConfiguration {

		/**
		 * Lays the review report out as PDF, xlsx and csv.
		 */
		@Bean
		@ConditionalOnMissingBean
		ReviewReportRenderer reviewReportRenderer() {
			return new DefaultReviewReportRenderer();
		}

		@Bean
		@ConditionalOnMissingBean
		AccountReviewReports accountReviewReports(AccountReviewReportRepository stored, ReviewReportRenderer renderer,
				AccountAuditLogger auditLogger) {
			return new AccountReviewReports(stored, renderer, auditLogger, Clock.systemUTC());
		}

		@Bean
		@ConditionalOnMissingBean
		AccountReviewService accountReviewService(TaskRepository tasks, AccountReviewItemRepository items,
				AccountReviewAttestationRepository attestations, AccountReviewPopulationEntryRepository entries,
				AppUserRepository users, AppGroupRepository groups, AccountAuditEventRepository auditEvents,
				AccountLifecycleService lifecycle, SessionRevocationService sessionRevocationService,
				AccountAuditLogger auditLogger, AccountReviewReports reports, Environment environment) {
			return new AccountReviewService(tasks, items, attestations, entries, users, groups, auditEvents, lifecycle,
					sessionRevocationService, auditLogger, reports, Clock.systemUTC(), zone(environment));
		}

		@Bean
		@ConditionalOnMissingBean
		AccountReviewScheduler accountReviewScheduler(TaskRepository tasks, AccountReviewService service,
				SettingsService settings, Environment environment) {
			return new AccountReviewScheduler(tasks, service, settings, Clock.systemUTC(), zone(environment));
		}

		private static ZoneId zone(Environment environment) {
			return environment.getProperty("commons.accounts.review.time-zone", ZoneId.class, ZoneId.systemDefault());
		}

	}

	/**
	 * The administration API.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBooleanProperty(name = "commons.accounts.admin.enabled", matchIfMissing = true)
	static class AdministrationConfiguration {

		@Bean
		AdministrationService administrationService(AppUserRepository users, AppGroupRepository groups,
				AppRoleRepository roles, SessionRevocationService sessionRevocationService,
				AccountAuditLogger accountAuditLogger) {
			return new AdministrationService(users, groups, roles, sessionRevocationService, accountAuditLogger);
		}

		@Bean
		SettingsController settingsController(SettingsService settingsService) {
			return new SettingsController(settingsService);
		}

		@Bean
		AuditEventController auditEventController(AccountAuditEventRepository events) {
			return new AuditEventController(events);
		}

		@Bean
		TaskController taskController(AccountReviewService service) {
			return new TaskController(service);
		}

		@Bean
		AccountReviewController accountReviewController(AccountReviewService service) {
			return new AccountReviewController(service);
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
						.addPathPatterns("/admin/users/**", "/admin/groups/**", "/admin/roles/**", "/admin/settings/**",
								"/account-reviews/**")
						.excludePathPatterns("/admin/users/sessions", "/admin/users/*/sessions");
				}

			};
		}

		@Bean
		UserAdminController userAdminController(AdministrationService administrationService,
				AccountLifecycleService accountLifecycleService) {
			return new UserAdminController(administrationService, accountLifecycleService);
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
