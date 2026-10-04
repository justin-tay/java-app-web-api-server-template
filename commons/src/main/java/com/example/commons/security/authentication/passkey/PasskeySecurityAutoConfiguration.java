package com.example.commons.security.authentication.passkey;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.util.Assert;

import com.example.commons.security.OidcLoginSecurityAutoConfiguration;
import com.example.commons.security.authentication.ProblemDetailAuthenticationEntryPoint;
import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.session.AbsoluteSessionTimeoutFilter;
import com.example.commons.security.session.SessionLifecycleAuditLogger;

/**
 * Adds passkey (WebAuthn) login and registration to the application's Spring Security
 * filter chain, on top of OpenID Connect login (see docs/adr/0024).
 *
 * <p>
 * Off unless {@code commons.security.passkeys.enabled} is {@code true}, in which case the
 * relying party ID and allowed origins must be configured, or startup fails, and the
 * application must define a {@link PasskeyUserDirectory} and a
 * {@link LocalAuthorityLookup}. A passkey is another way to log in as the same local
 * user, so the session carries the user's local roles, refreshed on every request like an
 * OpenID Connect session, and the passkey tables come from the accounts schema,
 * {@code com/example/commons/accounts/jdbc/schema.yaml}.
 *
 * <p>
 * Spring Security supplies the endpoints: {@code POST /webauthn/authenticate/options} and
 * {@code POST /login/webauthn} to log in, and {@code POST /webauthn/register/options},
 * {@code POST /webauthn/register}, and {@code DELETE /webauthn/register/{id}} to register
 * and remove a passkey. This configuration adds {@code GET /account/passkeys} and
 * {@code PATCH /account/passkeys/{id}} to list and rename them.
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class,
		afterName = { "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration" },
		after = OidcLoginSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ HttpSecurity.class, WebAuthnRelyingPartyOperations.class })
@ConditionalOnBooleanProperty(name = "commons.security.passkeys.enabled")
@EnableConfigurationProperties(PasskeyProperties.class)
public class PasskeySecurityAutoConfiguration {

	/**
	 * Order of {@link #passkeyFilterChainCustomizer}: after the OpenID Connect login
	 * customizer.
	 */
	public static final int FILTER_CHAIN_CUSTOMIZER_ORDER = OidcLoginSecurityAutoConfiguration.FILTER_CHAIN_CUSTOMIZER_ORDER
			+ 100;

	@Bean
	@ConditionalOnMissingBean
	PasskeyAuditLogger passkeyAuditLogger() {
		return new PasskeyAuditLogger();
	}

	@Bean
	PublicKeyCredentialUserEntityRepository passkeyUserEntityRepository(JdbcOperations jdbcOperations,
			ObjectProvider<PasskeyUserDirectory> directory) {
		return new DirectoryBackedUserEntityRepository(new JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations),
				requirePasskeyUserDirectory(directory));
	}

	@Bean
	AuditedUserCredentialRepository passkeyUserCredentialRepository(JdbcOperations jdbcOperations,
			PublicKeyCredentialUserEntityRepository userEntities, PasskeyAuditLogger auditLogger,
			PasskeyProperties properties) {
		return new AuditedUserCredentialRepository(new JdbcUserCredentialRepository(jdbcOperations), userEntities,
				auditLogger, properties.getMaxPerUser());
	}

	/**
	 * Provides the relying party operations with the passkey policy: discoverable
	 * credentials and user verification required, and attestation none (Spring Security's
	 * default), so the authenticator's make and model is not asked for.
	 * @param properties the passkey properties
	 * @param userEntities the passkey user entities
	 * @param userCredentials the passkey credentials
	 * @return the relying party operations
	 */
	@Bean
	WebAuthnRelyingPartyOperations passkeyRelyingPartyOperations(PasskeyProperties properties,
			PublicKeyCredentialUserEntityRepository userEntities, AuditedUserCredentialRepository userCredentials) {
		Assert.hasText(properties.getRelyingParty().getId(),
				"commons.security.passkeys.relying-party.id must be set when passkeys are enabled");
		Assert.notEmpty(properties.getAllowedOrigins(),
				"commons.security.passkeys.allowed-origins must be set when passkeys are enabled");
		String rpName = (properties.getRelyingParty().getName() != null) ? properties.getRelyingParty().getName()
				: properties.getRelyingParty().getId();
		Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(userEntities,
				userCredentials,
				PublicKeyCredentialRpEntity.builder().id(properties.getRelyingParty().getId()).name(rpName).build(),
				properties.getAllowedOrigins());
		operations.setCustomizeCreationOptions(
				options -> options.authenticatorSelection(AuthenticatorSelectionCriteria.builder()
					.userVerification(UserVerificationRequirement.REQUIRED)
					.residentKey(ResidentKeyRequirement.REQUIRED)
					.build()));
		operations
			.setCustomizeRequestOptions(options -> options.userVerification(UserVerificationRequirement.REQUIRED));
		return operations;
	}

	@Bean
	PasskeyManager passkeyManager(AuditedUserCredentialRepository userCredentials,
			PublicKeyCredentialUserEntityRepository userEntities) {
		return new JdbcPasskeyManager(userCredentials, userEntities);
	}

	@Bean
	PasskeyController passkeyController(PasskeyManager passkeyManager, ObjectProvider<PasskeyUserDirectory> directory) {
		return new PasskeyController(passkeyManager, requirePasskeyUserDirectory(directory));
	}

	@Bean
	PasskeyLocalAuthorityRefresher passkeyLocalAuthorityRefresher() {
		return new PasskeyLocalAuthorityRefresher();
	}

	/**
	 * Adds Spring Security's WebAuthn login and registration to every
	 * {@code HttpSecurity}, with the checks around them that this application needs: the
	 * passkey login gets session fixation protection and the concurrent-session limit of
	 * the other logins, records when it happened, and has a session lifetime of its own;
	 * registering a passkey needs a recent login and is limited per user.
	 * @param properties the passkey properties
	 * @param clock the clock, if the application defines one
	 * @param authenticationEntryPoint the entry point for unauthenticated requests
	 * @param userEntities the passkey user entities
	 * @param userCredentials the passkey credentials
	 * @param sessionLifecycleAuditLogger the session lifecycle audit logger
	 * @param localAuthorityLookup the local authority lookup
	 * @return the customizer
	 */
	@Bean
	@Order(FILTER_CHAIN_CUSTOMIZER_ORDER)
	Customizer<HttpSecurity> passkeyFilterChainCustomizer(PasskeyProperties properties, ObjectProvider<Clock> clock,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			PublicKeyCredentialUserEntityRepository userEntities, AuditedUserCredentialRepository userCredentials,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger,
			ObjectProvider<LocalAuthorityLookup> localAuthorityLookup) {
		Clock resolvedClock = clock.getIfAvailable(Clock::systemUTC);
		UserDetailsService userDetailsService = new LocalAuthorityUserDetailsService(
				localAuthorityLookup.getIfAvailable(() -> {
					throw new IllegalStateException("Passkeys require a LocalAuthorityLookup bean (see docs/adr/0024)");
				}));
		return http -> {
			http.setSharedObject(UserDetailsService.class, userDetailsService);
			http.addFilterAfter(new PasskeySessionFilters.AbsoluteTimeoutFilter(properties.getSessionAbsoluteTimeout(),
					resolvedClock, sessionLifecycleAuditLogger), AbsoluteSessionTimeoutFilter.class)
				.addFilterBefore(
						new PasskeyRegistrationGuardFilter(authenticationEntryPoint, userEntities, userCredentials,
								properties.getRegistrationMaxAge(), properties.getMaxPerUser(), resolvedClock),
						AuthorizationFilter.class)
				.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
					.requestMatchers(
							PathPatternRequestMatcher.withDefaults()
								.matcher(HttpMethod.POST, "/webauthn/authenticate/options"),
							PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login/webauthn"))
					.permitAll())
				.webAuthn(webAuthn -> webAuthn.disableDefaultRegistrationPage(true)
					.withObjectPostProcessor(new ObjectPostProcessor<Object>() {
						@Override
						public <O> O postProcess(O object) {
							if (object instanceof WebAuthnAuthenticationFilter filter) {
								filter.setSessionAuthenticationStrategy(
										http.getSharedObject(SessionAuthenticationStrategy.class));
								filter.setAuthenticationSuccessHandler(
										new PasskeySessionFilters.LoginRecorder(resolvedClock));
							}
							return object;
						}
					}));
		};
	}

	private static PasskeyUserDirectory requirePasskeyUserDirectory(ObjectProvider<PasskeyUserDirectory> directory) {
		return directory.getIfAvailable(() -> {
			throw new IllegalStateException("Passkeys require a PasskeyUserDirectory bean that finds the enabled "
					+ "local user for a username (see docs/adr/0024)");
		});
	}

}
