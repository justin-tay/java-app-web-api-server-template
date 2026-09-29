package com.example.commons.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.commons.accounts.admin.AdministrationService;
import com.example.commons.accounts.admin.GroupAdminController;
import com.example.commons.accounts.admin.RoleAdminController;
import com.example.commons.accounts.admin.UserAdminController;
import com.example.commons.accounts.domain.AppGroupRepository;
import com.example.commons.accounts.domain.AppRoleRepository;
import com.example.commons.accounts.domain.AppUserRepository;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.session.SessionRevocationService;

class AccountsAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AccountsAutoConfiguration.class))
		.withUserConfiguration(RepositoriesConfiguration.class);

	@Test
	void providesTheAuthorityLookupAndTheAdministrationApiByDefault() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(AppUserLocalAuthorityLookup.class)
			.hasSingleBean(AdministrationService.class)
			.hasSingleBean(UserAdminController.class)
			.hasSingleBean(GroupAdminController.class)
			.hasSingleBean(RoleAdminController.class));
	}

	@Test
	void addsTheAccountsPackageToEntityAndRepositoryScanning() {
		this.contextRunner.run(context -> assertThat(AutoConfigurationPackages.get(context.getBeanFactory()))
			.contains(AccountsAutoConfiguration.class.getPackageName()));
	}

	@Test
	void keepsTheModelWithoutTheAdministrationApiWhenAdminIsDisabled() {
		this.contextRunner.withPropertyValues("commons.accounts.admin.enabled=false")
			.run(context -> assertThat(context).hasSingleBean(AppUserLocalAuthorityLookup.class)
				.doesNotHaveBean(AdministrationService.class)
				.doesNotHaveBean(UserAdminController.class));
	}

	@Test
	void disablesDormantUsersOnlyWhenAThresholdIsSet() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(DormantUserDisabler.class));
		this.contextRunner.withPropertyValues("commons.accounts.dormancy.threshold=90d")
			.run(context -> assertThat(context).hasSingleBean(DormantUserDisabler.class));
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner.withPropertyValues("commons.accounts.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(AccountsAutoConfiguration.class)
				.doesNotHaveBean(AppUserLocalAuthorityLookup.class)
				.doesNotHaveBean(AdministrationService.class));
	}

	@Test
	void keepsAnApplicationAuthorityLookup() {
		this.contextRunner.withBean("applicationLookup", LocalAuthorityLookup.class, () -> username -> Optional.empty())
			.run(context -> assertThat(context).hasSingleBean(LocalAuthorityLookup.class)
				.doesNotHaveBean(AppUserLocalAuthorityLookup.class));
	}

	/**
	 * Stand-ins for the beans Spring Data JPA and commons security provide.
	 */
	@Configuration(proxyBeanMethods = false)
	static class RepositoriesConfiguration {

		@Bean
		AppUserRepository appUserRepository() {
			return mock(AppUserRepository.class);
		}

		@Bean
		AppGroupRepository appGroupRepository() {
			return mock(AppGroupRepository.class);
		}

		@Bean
		AppRoleRepository appRoleRepository() {
			return mock(AppRoleRepository.class);
		}

		@Bean
		SessionRevocationService sessionRevocationService() {
			return mock(SessionRevocationService.class);
		}

	}

}
