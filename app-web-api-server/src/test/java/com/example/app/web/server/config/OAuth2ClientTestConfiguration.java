package com.example.app.web.server.config;

import java.util.Optional;

import org.assertj.core.util.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Provider;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Registration;
import org.springframework.context.annotation.Configuration;

/**
 * Gives the tests a static client registration, so they need no Keycloak. A test of the
 * lazy discovery (see docs/adr/0029) sets {@code test.oauth2-client.static-registration}
 * to {@code false} to keep the {@code issuer-uri}.
 */
@Configuration
@ConditionalOnProperty(name = "test.oauth2-client.static-registration", havingValue = "true", matchIfMissing = true)
public class OAuth2ClientTestConfiguration {

	public OAuth2ClientTestConfiguration(Optional<OAuth2ClientProperties> properties) {
		if (properties.isPresent()) {
			for (Provider provider : properties.get().getProvider().values()) {
				// Clear issuer-uri so the registration needs no discovery request
				provider.setIssuerUri(null);
				// Set authorization-uri and token-uri to pass validation
				if (Strings.isNullOrEmpty(provider.getAuthorizationUri())) {
					provider.setAuthorizationUri("http://localhost:8080/realms/test/protocol/openid-connect/auth");
				}
				provider.setTokenUri("http://localhost:8080/realms/test/protocol/openid-connect/token");
			}
			for (Registration registration : properties.get().getRegistration().values()) {
				// Set redirect-uri to pass validation
				if (Strings.isNullOrEmpty(registration.getRedirectUri())) {
					registration.setRedirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}");
				}
			}
		}
	}

}
