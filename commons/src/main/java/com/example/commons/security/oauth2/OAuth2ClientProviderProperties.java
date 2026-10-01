package com.example.commons.security.oauth2;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-provider settings that Spring Boot's {@code spring.security.oauth2.client.provider}
 * has no property for, keyed by the same provider ID (see docs/adr/0029).
 */
@ConfigurationProperties(prefix = "commons.security.oauth2.client")
public class OAuth2ClientProviderProperties {

	/**
	 * Settings by provider ID, which must be a provider (or the provider of a
	 * registration) in {@code spring.security.oauth2.client}.
	 */
	private final Map<String, Provider> provider = new LinkedHashMap<>();

	public Map<String, Provider> getProvider() {
		return this.provider;
	}

	public static class Provider {

		/**
		 * Name of the {@code spring.ssl.bundle} whose trust material validates the
		 * provider's TLS certificate, for a provider whose certificate is issued by a CA
		 * the JVM does not trust. It applies to every call the application makes to the
		 * provider: discovery, the token endpoint, the JWK Set and the user info
		 * endpoint. Unset, the JVM's default trust applies.
		 */
		private String sslBundle;

		public String getSslBundle() {
			return this.sslBundle;
		}

		public void setSslBundle(String sslBundle) {
			this.sslBundle = sslBundle;
		}

	}

}
