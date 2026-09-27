package com.example.commons.security.authentication.passkey;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Passkey (WebAuthn) properties under {@code commons.security.passkeys}. Passkeys are off
 * unless {@code enabled} is set, and then {@code relying-party.id} and
 * {@code allowed-origins} must be set too, which {@link PasskeySecurityAutoConfiguration}
 * checks at startup (see docs/adr/0024).
 */
@ConfigurationProperties(prefix = "commons.security.passkeys")
@Validated
public class PasskeyProperties {

	/**
	 * Whether passkey login and registration are available.
	 */
	private boolean enabled = false;

	@Valid
	private final RelyingParty relyingParty = new RelyingParty();

	/**
	 * The exact origins, such as {@code https://app.example.com}, that may use a passkey.
	 */
	private Set<String> allowedOrigins = new LinkedHashSet<>();

	/**
	 * The most passkeys one user may hold.
	 */
	@Min(1)
	private int maxPerUser = 10;

	/**
	 * How recently the user must have logged in to register a passkey.
	 */
	@NotNull
	private Duration registrationMaxAge = Duration.ofMinutes(15);

	/**
	 * Maximum lifetime of a session created by a passkey login, from the login. A passkey
	 * session has no OpenID Provider session to end it, so this is its only backstop
	 * besides disabling the local user.
	 */
	@NotNull
	private Duration sessionAbsoluteTimeout = Duration.ofHours(8);

	public Duration getSessionAbsoluteTimeout() {
		return this.sessionAbsoluteTimeout;
	}

	public void setSessionAbsoluteTimeout(Duration sessionAbsoluteTimeout) {
		this.sessionAbsoluteTimeout = sessionAbsoluteTimeout;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public RelyingParty getRelyingParty() {
		return this.relyingParty;
	}

	public Set<String> getAllowedOrigins() {
		return this.allowedOrigins;
	}

	public void setAllowedOrigins(Set<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public int getMaxPerUser() {
		return this.maxPerUser;
	}

	public void setMaxPerUser(int maxPerUser) {
		this.maxPerUser = maxPerUser;
	}

	public Duration getRegistrationMaxAge() {
		return this.registrationMaxAge;
	}

	public void setRegistrationMaxAge(Duration registrationMaxAge) {
		this.registrationMaxAge = registrationMaxAge;
	}

	/**
	 * The WebAuthn relying party: the site every passkey is registered against.
	 */
	public static class RelyingParty {

		/**
		 * The relying party ID: the registrable domain every passkey is bound to, such as
		 * {@code app.example.com}. Changing it later invalidates every registered
		 * passkey.
		 */
		private String id;

		/**
		 * The relying party name the authenticator shows to the user.
		 */
		private String name;

		public String getId() {
			return this.id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
